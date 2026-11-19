"""Read/write checks for a separately launched JAR on aeroroster_test, port 18081.

Never targets port 8080 or aeroroster_dev. Only this run's returned fixture IDs
are cleaned up. Credentials use TEST_DB_*; no credentials or row dumps are logged.
Browser rendering and persistence after browser refresh require separate evidence.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
import zipfile
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:18081"
checks = 0
stale_operations = set()


def test_database():
    url = os.environ.get("TEST_DB_URL", "jdbc:postgresql://localhost:5432/aeroroster_test")
    match = re.fullmatch(r"jdbc:postgresql://(localhost|127\.0\.0\.1):([0-9]+)/aeroroster_test", url)
    assert match and 1 <= int(match[2]) <= 65535, "Only a loopback aeroroster_test database is permitted"
    return url, match[1], match[2]


def sql(statement):
    executable = os.environ.get("PSQL_EXE") or shutil.which("psql")
    if not executable:
        raise RuntimeError("Add psql to PATH or set PSQL_EXE to its installed executable")
    env = os.environ.copy()
    env["PGPASSWORD"] = env.get("TEST_DB_PASSWORD", "")
    env["PGCONNECT_TIMEOUT"] = "5"
    _, host, port = test_database()
    result = subprocess.run(
        [executable, "-X", "-w", "-h", host, "-p", port, "-U", env.get("TEST_DB_USER", "postgres"),
         "-d", "aeroroster_test", "-At", "-v", "ON_ERROR_STOP=1"],
        input=statement, text=True, capture_output=True, env=env, check=False)
    if result.returncode:
        raise AssertionError("Dedicated test database check failed; inspect local credentials privately")
    return result.stdout.strip()


def request(method, path, body=None, status=200, snapshot=None, headers=None, raw=False):
    global checks
    headers = dict(headers or {})
    if snapshot is not None:
        headers["X-Expected-Version"] = f'{snapshot["id"]}:{snapshot["version"]}'
    req = Request(BASE + path, method=method, headers={"Content-Type": "application/json", **headers},
                  data=json.dumps(body).encode() if body is not None else None)
    try:
        response = urlopen(req, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        payload = response.read()
        assert response.status == status, f"{method} {path}: expected {status}, got {response.status}"
        assert not response.headers.get("Access-Control-Allow-Origin")
        content_type = response.headers.get("Content-Type", "")
    checks += 1
    return (payload, content_type) if raw else json.loads(payload) if payload else None


def stale(method, path, body, original):
    resource = re.sub(r"/(activity|status|cancellation|reassignments|expiry)$", "", path)
    before = request("GET", resource)
    assert request(method, path, body, 409, snapshot=original)["code"] == "STALE_VERSION"
    assert request("GET", resource) == before, "A rejected stale mutation must not change the resource"
    stale_operations.add((method, path))


def main():
    # Guard both the SQL target and the independently verified JAR process/config.
    database_url, _, _ = test_database()
    assert sql("select current_database()") == "aeroroster_test"
    jar = Path(os.environ["PHASE310_JAR"])
    assert jar.is_file()
    pid = int(os.environ["PHASE310_PID"])
    assert pid > 0
    process = subprocess.run(
        ["powershell.exe", "-NoProfile", "-Command",
         f'(Get-CimInstance Win32_Process -Filter "ProcessId={pid}").CommandLine'],
        text=True, capture_output=True, check=True)
    command = process.stdout
    assert str(jar) in command, "Expected the owned test JAR process"
    assert "--spring.datasource.url=" + database_url in command
    assert "--server.port=18081" in command
    assert "--server.address=127.0.0.1" in command
    html, content_type = request("GET", "/", raw=True)
    assert "text/html" in content_type
    latest_dist = Path(__file__).resolve().parents[1] / "frontend" / "dist"
    assert html == (latest_dist / "index.html").read_bytes(), "JAR must serve the latest independently built frontend"
    with zipfile.ZipFile(jar) as archive:
        assert html == archive.read("BOOT-INF/classes/static/index.html")
        for asset in re.findall(rb'(?:src|href)="(/assets/[^\"]+)"', html):
            path = asset.decode()
            payload, mime = request("GET", path, raw=True)
            assert payload == archive.read("BOOT-INF/classes/static" + path)
            assert payload == (latest_dist / path.lstrip("/")).read_bytes()
            assert ("javascript" in mime if path.endswith(".js") else "text/css" in mime)
    for route in ["/dashboard", "/crew", "/crew/1", "/aircraft", "/aircraft/1", "/flights", "/flights/1",
                  "/qualifications", "/qualifications/1", "/assignments", "/assignments/1",
                  "/unavailability", "/unavailability/1"]:
        assert request("GET", route, raw=True)[0] == html
    for path in ["/api/v1/not-a-route", "/assets/missing.js", "/assets/missing.css"]:
        assert request("GET", path, status=404)["code"] == "NOT_FOUND"
    assert request("GET", "/api/v1/airports", headers={"Origin": BASE})
    assert request("GET", "/api/v1/airports", status=403, headers={"Origin": "http://127.0.0.1:5173"})["code"] == "ORIGIN_NOT_ALLOWED"
    assert request("GET", "/api/v1/airports", status=403, headers={"Host": "rebind.example"})["code"] == "HOST_NOT_ALLOWED"
    airports = request("GET", "/api/v1/airports")
    aircraft_type = request("GET", "/api/v1/aircraft-types")[0]
    qualification = next(q for q in request("GET", "/api/v1/qualifications")
                         if q["aircraftTypeId"] == aircraft_type["id"] and q["requiredRole"] == "PILOT")
    assert request("POST", "/api/v1/crew", {}, 400)["fieldErrors"]
    assert sql("select count(*) from flyway_schema_history where success and version in ('1','2','3')") == "3"
    crew = plane = flight = completed_flight = None
    tag = "P310" + uuid.uuid4().hex[:7].upper()
    try:
        crew_body = {"name": "Disposable integration pilot", "role": "PILOT", "licenseNo": tag,
                     "experienceYears": 3, "contactNo": "9000000000"}
        crew = request("POST", "/api/v1/crew", crew_body, 201)
        owner = crew["id"]
        crew_path = f"/api/v1/crew/{owner}"
        request("PUT", crew_path, crew_body, 428)
        changed = request("PUT", crew_path, {**crew_body, "name": "Reviewed pilot"}, snapshot=crew)
        stale("PUT", crew_path, crew_body, crew)
        assert request("GET", crew_path)["name"] == "Reviewed pilot"
        activity_original = changed
        changed = request("PATCH", crew_path + "/activity", {"active": False}, snapshot=changed)
        stale("PATCH", crew_path + "/activity", {"active": False}, activity_original)
        changed = request("PATCH", crew_path + "/activity", {"active": True}, snapshot=changed)
        assert owner in {x["id"] for x in request("GET", f"/api/v1/crew?q={tag}&size=200")["items"]}
        held_path = crew_path + f'/qualifications/{qualification["id"]}'
        held = request("POST", crew_path + "/qualifications", {"qualificationId": qualification["id"], "validUntil": "2027-12-31"}, 201)
        held_original = held
        held = request("PUT", held_path + "/expiry", {"validUntil": None}, snapshot=held)
        stale("PUT", held_path + "/expiry", {"validUntil": None}, held_original)
        stale("DELETE", held_path, None, held_original)
        assert request("GET", held_path)["validUntil"] is None
        request("DELETE", held_path, status=204, snapshot=held)
        held = request("POST", crew_path + "/qualifications", {"qualificationId": qualification["id"], "validUntil": None}, 201)
        assert held["id"] in {x["id"] for x in request("GET", crew_path + "/qualifications")}
        period_body = {"startTime": "2026-12-28T08:00:00Z", "endTime": "2026-12-28T10:00:00Z", "reason": "Disposable leave"}
        period = request("POST", crew_path + "/unavailability", period_body, 201)
        period_original = period
        period_path = crew_path + f'/unavailability/{period["id"]}'
        period = request("PUT", period_path, {**period_body, "reason": "Reviewed leave"}, snapshot=period)
        stale("PUT", period_path, period_body, period_original)
        stale("DELETE", period_path, None, period_original)
        assert request("GET", period_path)["reason"] == "Reviewed leave"
        assert request("GET", crew_path + "/unavailability?size=200")["totalElements"] == 1
        assert request("GET", crew_path + "/unavailability/conflicts?start=2026-12-28T08:00:00Z&end=2026-12-28T10:00:00Z")["conflicts"]
        request("POST", crew_path + "/unavailability", period_body, 409)
        request("DELETE", period_path, status=204, snapshot=period)
        plane = request("POST", "/api/v1/aircraft", {"registration": tag, "aircraftTypeId": aircraft_type["id"]}, 201)
        plane_path = f'/api/v1/aircraft/{plane["id"]}'
        plane_original = plane
        plane = request("PUT", plane_path, {"registration": tag + "X", "aircraftTypeId": aircraft_type["id"]}, snapshot=plane)
        stale("PUT", plane_path, {"registration": tag, "aircraftTypeId": aircraft_type["id"]}, plane_original)
        plane_activity_original = plane
        plane = request("PATCH", plane_path + "/activity", {"active": False}, snapshot=plane)
        stale("PATCH", plane_path + "/activity", {"active": False}, plane_activity_original)
        plane = request("PATCH", plane_path + "/activity", {"active": True}, snapshot=plane)
        assert plane["id"] in {x["id"] for x in request("GET", f"/api/v1/aircraft?q={tag}&size=200")["items"]}
        details = {"flightNumber": tag, "sourceAirportId": airports[0]["id"], "destinationAirportId": airports[1]["id"],
                   "aircraftId": plane["id"], "departureTime": "2026-12-30T08:00:00Z", "arrivalTime": "2026-12-30T10:00:00Z"}
        flight = request("POST", "/api/v1/flights", details, 201)
        flight_path = f'/api/v1/flights/{flight["id"]}'
        flight_original = flight
        flight = request("PUT", flight_path, {**details, "flightNumber": tag + "X"}, snapshot=flight)
        stale("PUT", flight_path, details, flight_original)
        assert request("GET", flight_path)["flightNumber"] == tag + "X"
        assert flight["id"] in {x["id"] for x in request("GET", f"/api/v1/flights?flightNumber={tag}&size=200")["items"]}
        assignment_body = {"crewId": owner, "flightId": flight["id"], "assignedRole": "PILOT"}
        assert request("POST", "/api/v1/assignments/eligibility", assignment_body)["eligible"]
        assignment = request("POST", "/api/v1/assignments", assignment_body, 201)
        assert not request("POST", "/api/v1/assignments/eligibility", assignment_body)["eligible"]
        request("POST", "/api/v1/assignments", assignment_body, 409)
        cover = request("GET", flight_path + "/coverage")
        assert not cover["minimumStaffingSatisfied"] and cover["policyNotice"]
        duty_period = {"startTime": "2026-12-30T08:00:00Z", "endTime": "2026-12-30T09:00:00Z", "reason": "Conflict probe"}
        request("POST", crew_path + "/unavailability", duty_period, 409)
        assignment_path = f'/api/v1/assignments/{assignment["id"]}'
        cancelled = request("PUT", assignment_path + "/cancellation", {}, snapshot=assignment)
        assert request("GET", assignment_path)["status"] == "CANCELLED"
        stale("PUT", assignment_path + "/cancellation", {}, assignment)
        stale("POST", assignment_path + "/reassignments", {}, assignment)
        replacement = request("POST", assignment_path + "/reassignments", {}, 201, snapshot=cancelled)
        assert replacement["id"] != assignment["id"]
        request("PUT", f'/api/v1/assignments/{replacement["id"]}/cancellation', {}, snapshot=replacement)
        assert request("GET", flight_path + "/assignments?activeOnly=false")["totalElements"] == 2
        assert request("GET", crew_path + "/assignments?activeOnly=false")["totalElements"] == 2
        assert request("GET", plane_path + "/flights")["totalElements"] == 1
        cancellation_original = flight
        flight = request("PUT", flight_path + "/cancellation", {}, snapshot=flight)
        stale("PUT", flight_path + "/cancellation", {}, cancellation_original)
        assert request("GET", flight_path)["status"] == "CANCELLED"
        past = {**details, "flightNumber": tag + "P", "departureTime": "2026-11-17T08:00:00Z", "arrivalTime": "2026-11-17T10:00:00Z"}
        completed_flight = request("POST", "/api/v1/flights", past, 201)
        completed_path = f'/api/v1/flights/{completed_flight["id"]}'
        status_original = completed_flight
        completed_flight = request("PATCH", completed_path + "/status", {"status": "COMPLETED"}, snapshot=completed_flight)
        stale("PATCH", completed_path + "/status", {"status": "COMPLETED"}, status_original)
        assert request("GET", completed_path)["status"] == "COMPLETED"
        # Verify persisted values directly, without exporting database records.
        assert sql(f"select count(*) from crew where id={owner} and name='Reviewed pilot'") == "1"
        assert sql(f"select count(*) from duty_assignment where crew_id={owner} and status='CANCELLED'") == "2"
        assert len(stale_operations) == 13
    finally:
        assert sql("select current_database()") == "aeroroster_test"
        statements = ["begin;"]
        if crew:
            statements += [f'delete from {table} where crew_id={crew["id"]};' for table in
                           ("duty_assignment", "crew_unavailability", "crew_qualification")]
        if flight:
            statements += [f'delete from flight where id={flight["id"]};', f'delete from flight_audit where flight_id={flight["id"]};']
        if completed_flight:
            statements += [f'delete from flight where id={completed_flight["id"]};', f'delete from flight_audit where flight_id={completed_flight["id"]};']
        if plane:
            statements += [f'delete from aircraft where id={plane["id"]};']
        if crew:
            statements += [f'delete from crew where id={crew["id"]};']
        sql("\n".join(statements + ["commit;"]))
        if crew:
            assert sql(f'select count(*) from crew where id={crew["id"]}') == "0"
    print(f"Phase 3.10 test JAR: {checks} HTTP checks passed; persisted values verified; owned fixtures cleaned")
    print(f"Protected mutation stale-token rejection: {len(stale_operations)}/13 operations")
    print("Packaged JAR SHA256:", hashlib.sha256(jar.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()

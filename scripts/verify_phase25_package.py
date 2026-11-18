"""Real HTTP verification of the current backend JAR on 127.0.0.1:18081.

Run the JAR against aeroroster_test first. Uses stdlib Python and installed psql;
credentials come from TEST_DB_USER/TEST_DB_PASSWORD (never printed). Deletes
only IDs returned for this run's own fixtures, never resets a database.
"""
import json
import os
import re
import subprocess
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:18081"
checked = 0


def sql(statement):
    env = os.environ.copy()
    env["PGPASSWORD"] = env.get("TEST_DB_PASSWORD", "")
    result = subprocess.run(
        ["psql", "-X", "-h", "localhost", "-U", env.get("TEST_DB_USER", "postgres"),
         "-d", "aeroroster_test", "-At", "-v", "ON_ERROR_STOP=1"],
        input=statement, text=True, capture_output=True, env=env, check=False)
    if result.returncode:
        raise AssertionError("Test database verification/cleanup failed; inspect locally without publishing credentials")
    return result.stdout.strip()


def request(method, path, body=None, status=200, headers=None, raw=False, conditional=True):
    global checked
    headers = dict(headers or {})
    mutation = method in ("PUT", "PATCH", "DELETE") or method == "POST" and path.endswith("/reassignments")
    if conditional and mutation and "X-Expected-Version" not in headers:
        current = request("GET", re.sub(r"/(activity|status|cancellation|reassignments|expiry)$", "", path))
        headers["X-Expected-Version"] = f'{current["id"]}:{current["version"]}'
    data = body.encode() if isinstance(body, str) else json.dumps(body).encode() if body is not None else None
    req = Request(BASE + path, data=data, method=method,
                  headers={"Content-Type": "application/json", **headers})
    try:
        response = urlopen(req, timeout=20)
    except HTTPError as error:
        response = error
    with response:
        payload = response.read().decode()
        assert response.status == status, f"{method} {path}: expected {status}, got {response.status}"
        assert not response.headers.get("Access-Control-Allow-Origin")
        if status == 201:
            assert response.headers["Location"].startswith("/api/v1/")
    checked += 1
    return payload if raw else json.loads(payload) if payload else None


def main():
    assert sql("select current_database()") == "aeroroster_test"
    crew = plane = flight = None
    tag = "P25" + uuid.uuid4().hex[:7]
    try:
        assert "AeroRoster" in request("GET", "/", raw=True)
        assert request("GET", "/css/app.css", raw=True)
        assert "AeroRoster" in request("GET", "/api-docs/index.html", raw=True)
        assert "fetch" in request("GET", "/api-docs/docs.js", raw=True)
        spec = request("GET", "/api/v1/openapi.json")
        assert sum(len(path) for path in spec["paths"].values()) == 42
        assert "HOST_NOT_ALLOWED" in spec["components"]["schemas"]["ApiError"]["properties"]["code"]["enum"]
        airports = request("GET", "/api/v1/airports")
        source = next(a["id"] for a in airports if a["iataCode"] == "DEL")
        destination = next(a["id"] for a in airports if a["iataCode"] == "BOM")
        types = request("GET", "/api/v1/aircraft-types")
        aircraft_type = next(t["id"] for t in types if t["model"] == "Airbus A320")
        requirements = request("GET", f"/api/v1/aircraft-types/{aircraft_type}/qualifications")
        qualification = next(q["id"] for q in requirements if q["requiredRole"] == "PILOT")
        request("GET", "/api/v1/crew?size=0", status=400)
        request("GET", "/api/v1/crew?page=2147483647&size=200", status=400)
        assert request("GET", "/api/v1/crew?page=2147483647&size=1")["last"]
        request("GET", "/api/v1/crew/9223372036854775807", status=404)
        request("GET", "/api/v1/crew", headers={"Origin": "http://foreign.example"}, status=403)
        blocked = request("GET", "/api/v1/crew", headers={"Host": "rebind.example", "Origin": "http://rebind.example"}, status=403)
        assert blocked["code"] == "HOST_NOT_ALLOWED"
        request("GET", "/api/v1/crew", headers={"Host": "rebind.example"}, status=403)
        request("GET", "/api/v1/crew?size=1", headers={"Origin": BASE})
        duplicate = request("POST", "/api/v1/crew", '{"role":"PILOT","role":"COPILOT"}', status=400)
        assert duplicate["code"] == "MALFORMED_JSON"
        crew_body = {"name": "Package verification pilot", "role": "PILOT",
                     "licenseNo": tag, "experienceYears": 3, "contactNo": "9000000000"}
        snapshot = request("POST", "/api/v1/crew", crew_body, 201)
        crew = snapshot["id"]
        crew_path = f"/api/v1/crew/{crew}"
        request("PUT", crew_path, crew_body, 428, conditional=False)
        token = {"X-Expected-Version": f'{crew}:{snapshot["version"]}'}
        request("PUT", crew_path, {**crew_body, "name": "First administrator"}, headers=token)
        stale = request("PUT", crew_path, {**crew_body, "name": "Stale administrator"}, 409, headers=token)
        assert stale["code"] == "STALE_VERSION"
        assert request("GET", crew_path)["name"] == "First administrator"
        request("POST", f"/api/v1/crew/{crew}/qualifications", {"qualificationId": qualification, "validUntil": "2027-12-31"}, 201)
        plane = request("POST", "/api/v1/aircraft", {"registration": tag, "aircraftTypeId": aircraft_type}, 201)["id"]
        details = {"flightNumber": tag, "aircraftId": plane, "sourceAirportId": source,
                   "destinationAirportId": destination, "departureTime": "2026-12-30T05:00:00Z", "arrivalTime": "2026-12-30T07:00:00Z"}
        flight = request("POST", "/api/v1/flights", details, 201)["id"]
        assignment = {"crewId": crew, "flightId": flight, "assignedRole": "PILOT"}
        assert request("POST", "/api/v1/assignments/eligibility", assignment)["eligible"]
        original = request("POST", "/api/v1/assignments", assignment, 201)
        assert original["dutyStartTime"] == "2026-12-30T04:00:00Z"
        assert original["dutyEndTime"] == "2026-12-30T07:30:00Z"
        request("POST", "/api/v1/assignments", assignment, 409)
        request("PUT", f"/api/v1/flights/{flight}", {**details, "flightNumber": tag + "X"}, 409)
        coverage = request("GET", f"/api/v1/flights/{flight}/coverage")
        assert not coverage["minimumStaffingSatisfied"] and "Illustrative" in coverage["policyNotice"]
        request("PUT", f'/api/v1/assignments/{original["id"]}/cancellation', {})
        replacement = request("POST", f'/api/v1/assignments/{original["id"]}/reassignments', {}, 201)
        assert replacement["id"] != original["id"]
        assert request("GET", f"/api/v1/crew/{crew}/assignments?activeOnly=true")["totalElements"] == 1
        request("PUT", f'/api/v1/assignments/{replacement["id"]}/cancellation', {})
        request("PUT", f"/api/v1/flights/{flight}/cancellation", {})
        assert request("GET", f"/api/v1/flights/{flight}/assignments?activeOnly=false")["totalElements"] == 2
        assert sql(f"select string_agg(action,',' order by id) from flight_audit where flight_id={flight}") == "INSERT,UPDATE"
    finally:
        statements = ["begin;"]
        if crew is not None:
            statements += [f"delete from {table} where crew_id={crew};" for table in
                           ("duty_assignment", "crew_unavailability", "crew_qualification")]
        if flight is not None:
            statements += [f"delete from flight where id={flight};", f"delete from flight_audit where flight_id={flight};"]
        if plane is not None:
            statements += [f"delete from aircraft where id={plane};"]
        if crew is not None:
            statements += [f"delete from crew where id={crew};"]
        sql("\n".join(statements + ["commit;"]))
        owned = []
        if crew is not None:
            owned += [f"(select count(*) from {table} where crew_id={crew})" for table in
                      ("duty_assignment", "crew_unavailability", "crew_qualification")]
            owned += [f"(select count(*) from crew where id={crew})"]
        if flight is not None:
            owned += [f"(select count(*) from flight where id={flight})",
                      f"(select count(*) from flight_audit where flight_id={flight})"]
        if plane is not None:
            owned += [f"(select count(*) from aircraft where id={plane})"]
        assert sql("select " + "+".join(owned or ["0"])) == "0", "Owned fixture cleanup was incomplete"
    print(f"Packaged JAR: {checked} HTTP checks passed; persisted audit verified; owned fixtures cleaned")


if __name__ == "__main__":
    main()

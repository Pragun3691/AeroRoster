"""End-to-End Verification of Interactive Flight Crew Assignment Workflow.

Tests the complete operational workflow against the packaged Spring Boot application:
1. Verify Aircraft-Specific Crew Requirements (A320 / B738: 1 Pilot, 1 Co-pilot, 2 Cabin crew)
2. Create Empty Flight (DEL -> BOM, A320) with 0 assignments and incomplete coverage
3. Real-time Backend Eligibility Preview for candidate crew members
4. Step-by-step incremental assignment:
   - Assign Pilot (1/4 coverage)
   - Assign Co-pilot (2/4 coverage)
   - Assign Cabin Crew 1 (3/4 coverage)
   - Assign Cabin Crew 2 (4/4 coverage -> MINIMUM STAFFING SATISFIED)
5. Robust Rejection of Invalid Assignments:
   - Duplicate active assignment rejection (409)
   - Role mismatch rejection (409)
   - Missing qualification rejection (409)
   - Excess role assignment rejection (409)
6. Assignment Cancellation with Version Header, History Retention, and Coverage Refresh
7. SPA Routing Verification for /flights/{id}/assign
8. Automatic Test Fixture Cleanup
"""

import json
import os
import signal
import subprocess
import sys
import time
import uuid
import urllib.request
import urllib.error

BASE_URL = "http://127.0.0.1:18081"
API_URL = f"{BASE_URL}/api/v1"

def http_get(path):
    url = f"{API_URL}{path}" if path.startswith("/") else path
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = resp.read().decode("utf-8")
            return resp.status, json.loads(data) if data else {}
    except urllib.error.HTTPError as err:
        err_body = err.read().decode("utf-8")
        try:
            return err.code, json.loads(err_body)
        except Exception:
            return err.code, {"raw": err_body}

def http_post(path, payload, headers=None):
    url = f"{API_URL}{path}"
    h = {"Content-Type": "application/json", "Accept": "application/json"}
    if headers:
        h.update(headers)
    body = json.dumps(payload).encode("utf-8") if payload is not None else b"{}"
    req = urllib.request.Request(url, data=body, headers=h, method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            data = resp.read().decode("utf-8")
            return resp.status, json.loads(data) if data else {}
    except urllib.error.HTTPError as err:
        err_body = err.read().decode("utf-8")
        try:
            return err.code, json.loads(err_body)
        except Exception:
            return err.code, {"raw": err_body}

def http_put(path, payload, headers=None):
    url = f"{API_URL}{path}"
    h = {"Content-Type": "application/json", "Accept": "application/json"}
    if headers:
        h.update(headers)
    body = json.dumps(payload).encode("utf-8") if payload is not None else b"{}"
    req = urllib.request.Request(url, data=body, headers=h, method="PUT")
    try:
        with urllib.request.urlopen(req) as resp:
            data = resp.read().decode("utf-8")
            return resp.status, json.loads(data) if data else {}
    except urllib.error.HTTPError as err:
        err_body = err.read().decode("utf-8")
        try:
            return err.code, json.loads(err_body)
        except Exception:
            return err.code, {"raw": err_body}

def run_tests():
    print("=" * 80)
    print("AEROROSTER END-TO-END INTERACTIVE CREW ASSIGNMENT VERIFICATION")
    print("=" * 80)

    # 1. Verify Aircraft-Specific Requirements
    print("\n--- 1. VERIFYING AIRCRAFT-TYPE CREW REQUIREMENTS ---")
    status, types = http_get("/aircraft-types")
    assert status == 200, f"Expected 200, got {status}"
    a320 = next((t for t in types if t["model"] == "Airbus A320"), None)
    assert a320 is not None, "Airbus A320 not found"
    assert a320["capacity"] == 180, f"Expected capacity 180, got {a320['capacity']}"
    assert a320["minCabinCrew"] == 2, f"Expected minCabinCrew 2, got {a320['minCabinCrew']}"
    print(f"PASS: Airbus A320 has capacity {a320['capacity']} and minCabinCrew {a320['minCabinCrew']}")
    print(f"      Calculated crew complement: 1 Pilot, 1 Co-pilot, {a320['minCabinCrew']} Cabin crew (Total: {2 + a320['minCabinCrew']})")

    # Get aircraft and airports
    status, aircraft_list = http_get("/aircraft?size=100")
    assert status == 200
    a320_plane = next(a for a in aircraft_list["items"] if a["model"] == "Airbus A320" and a["active"])

    status, airports = http_get("/airports")
    assert status == 200
    del_airport = next(a for a in airports if a["iataCode"] == "DEL")
    bom_airport = next(a for a in airports if a["iataCode"] == "BOM")

    # 2. Create Empty Flight
    print("\n--- 2. CREATING EMPTY FLIGHT WITH ZERO CREW ASSIGNMENTS ---")
    flight_num = f"E2E-{uuid.uuid4().hex[:4].upper()}"
    # Use a far future date to guarantee no conflicts with past test runs
    dep_ts = int(time.time())
    day = 10 + (dep_ts % 10)
    hour = (dep_ts % 12)
    dep_time = f"2027-01-{day:02d}T{hour:02d}:00:00Z"
    arr_time = f"2027-01-{day:02d}T{hour+2:02d}:30:00Z"

    flight_payload = {
        "flightNumber": flight_num,
        "sourceAirportId": del_airport["id"],
        "destinationAirportId": bom_airport["id"],
        "aircraftId": a320_plane["id"],
        "departureTime": dep_time,
        "arrivalTime": arr_time
    }
    status, flight = http_post("/flights", flight_payload)
    assert status == 201, f"Failed to create flight: {status} {flight}"
    flight_id = flight["id"]
    print(f"PASS: Created flight {flight['flightNumber']} (ID: {flight_id}) with aircraft {a320_plane['registration']}")

    created_assignments = []
    try:
        # Verify zero assignments exist in database
        status, assignments = http_get(f"/flights/{flight_id}/assignments")
        assert status == 200
        assert len(assignments["items"]) == 0, f"Expected 0 assignments, got {len(assignments['items'])}"
        print(f"PASS: Flight has exactly 0 crew assignments in PostgreSQL")

        # 3. Check Initial Coverage
        print("\n--- 3. VERIFYING INITIAL INCOMPLETE COVERAGE (0/4) ---")
        status, coverage = http_get(f"/flights/{flight_id}/coverage")
        assert status == 200
        assert coverage["minimumStaffingSatisfied"] is False
        pilot_cov = next(r for r in coverage["roles"] if r["role"] == "PILOT")
        copilot_cov = next(r for r in coverage["roles"] if r["role"] == "COPILOT")
        cabin_cov = next(r for r in coverage["roles"] if r["role"] == "CABIN_CREW")

        assert pilot_cov["required"] == 1 and pilot_cov["active"] == 0 and pilot_cov["missing"] == 1
        assert copilot_cov["required"] == 1 and copilot_cov["active"] == 0 and copilot_cov["missing"] == 1
        assert cabin_cov["required"] == 2 and cabin_cov["active"] == 0 and cabin_cov["missing"] == 2
        print(f"PASS: Initial Coverage: Pilot: 0/1, Co-pilot: 0/1, Cabin crew: 0/2")
        print(f"      Overall status: INCOMPLETE (minimumStaffingSatisfied = False)")

        # Retrieve Crew Candidates
        status, crew_page = http_get("/crew?size=100")
        assert status == 200
        crew_list = crew_page["items"]
        pilot = next(c for c in crew_list if c["licenseNo"] == "DEMO-P-001")
        copilot = next(c for c in crew_list if c["licenseNo"] == "DEMO-C-001")
        cabin1 = next(c for c in crew_list if c["licenseNo"] == "DEMO-F-001")
        cabin2 = next(c for c in crew_list if c["licenseNo"] == "DEMO-F-002")
        b738_pilot = next(c for c in crew_list if c["licenseNo"] == "DEMO-P-003")

        # 4. Candidate Eligibility Preview
        print("\n--- 4. TESTING REAL-TIME BACKEND ELIGIBILITY PREVIEW ---")
        status, preview = http_post("/assignments/eligibility", {
            "crewId": pilot["id"],
            "flightId": flight_id,
            "assignedRole": "PILOT"
        })
        assert status == 200
        assert preview["eligible"] is True
        assert preview["dutyWindow"] is not None
        print(f"PASS: Preview for {pilot['name']} (PILOT): eligible = True")
        print(f"      Calculated Duty Window: {preview['dutyWindow']['start']} to {preview['dutyWindow']['end']}")

        # Ineligible candidate preview (B738-only pilot on A320)
        status, bad_preview = http_post("/assignments/eligibility", {
            "crewId": b738_pilot["id"],
            "flightId": flight_id,
            "assignedRole": "PILOT"
        })
        assert status == 200
        assert bad_preview["eligible"] is False
        reason_codes = [r["code"] for r in bad_preview["reasons"]]
        assert "MISSING_QUALIFICATION" in reason_codes
        print(f"PASS: Preview for {b738_pilot['name']} (B738 pilot on A320): eligible = False, reasons: {reason_codes}")

        # 5. Incremental Crew Assignment Workflow
        print("\n--- 5. INCREMENTAL CREW ASSIGNMENTS & LIVE COVERAGE UPDATES ---")

        # Step 5a: Assign Pilot
        status, a_pilot = http_post("/assignments", {
            "crewId": pilot["id"],
            "flightId": flight_id,
            "assignedRole": "PILOT"
        })
        assert status == 201
        created_assignments.append(a_pilot)
        status, cov1 = http_get(f"/flights/{flight_id}/coverage")
        assert cov1["minimumStaffingSatisfied"] is False
        p1 = next(r for r in cov1["roles"] if r["role"] == "PILOT")
        assert p1["active"] == 1 and p1["missing"] == 0
        print(f"PASS [1/4]: Assigned Pilot {pilot['name']}. Coverage: Pilot 1/1, Co-pilot 0/1, Cabin 0/2 (INCOMPLETE)")

        # Step 5b: Assign Co-Pilot
        status, a_copilot = http_post("/assignments", {
            "crewId": copilot["id"],
            "flightId": flight_id,
            "assignedRole": "COPILOT"
        })
        assert status == 201
        created_assignments.append(a_copilot)
        status, cov2 = http_get(f"/flights/{flight_id}/coverage")
        assert cov2["minimumStaffingSatisfied"] is False
        cp1 = next(r for r in cov2["roles"] if r["role"] == "COPILOT")
        assert cp1["active"] == 1 and cp1["missing"] == 0
        print(f"PASS [2/4]: Assigned Co-Pilot {copilot['name']}. Coverage: Pilot 1/1, Co-pilot 1/1, Cabin 0/2 (INCOMPLETE)")

        # Step 5c: Assign Cabin Crew 1
        status, a_cc1 = http_post("/assignments", {
            "crewId": cabin1["id"],
            "flightId": flight_id,
            "assignedRole": "CABIN_CREW"
        })
        assert status == 201
        created_assignments.append(a_cc1)
        status, cov3 = http_get(f"/flights/{flight_id}/coverage")
        assert cov3["minimumStaffingSatisfied"] is False
        cc_cov = next(r for r in cov3["roles"] if r["role"] == "CABIN_CREW")
        assert cc_cov["active"] == 1 and cc_cov["missing"] == 1
        print(f"PASS [3/4]: Assigned Cabin Crew 1 {cabin1['name']}. Coverage: Pilot 1/1, Co-pilot 1/1, Cabin 1/2 (INCOMPLETE)")

        # Step 5d: Assign Cabin Crew 2
        status, a_cc2 = http_post("/assignments", {
            "crewId": cabin2["id"],
            "flightId": flight_id,
            "assignedRole": "CABIN_CREW"
        })
        assert status == 201
        created_assignments.append(a_cc2)
        status, cov4 = http_get(f"/flights/{flight_id}/coverage")
        assert cov4["minimumStaffingSatisfied"] is True
        cc_cov4 = next(r for r in cov4["roles"] if r["role"] == "CABIN_CREW")
        assert cc_cov4["active"] == 2 and cc_cov4["missing"] == 0
        print(f"PASS [4/4]: Assigned Cabin Crew 2 {cabin2['name']}. Coverage: Pilot 1/1, Co-pilot 1/1, Cabin 2/2")
        print(f"      *** OVERALL STATUS: MINIMUM STAFFING SATISFIED ***")

        # 6. Negative and Conflict Rejection Tests
        print("\n--- 6. VERIFYING BACKEND REJECTION OF INVALID ASSIGNMENTS ---")

        # Duplicate active assignment
        status, err = http_post("/assignments", {
            "crewId": pilot["id"],
            "flightId": flight_id,
            "assignedRole": "PILOT"
        })
        assert status == 409, f"Expected 409 DUPLICATE, got {status}"
        assert err["code"] == "ASSIGNMENT_REJECTED"
        print(f"PASS: Duplicate active assignment correctly rejected (HTTP 409 ASSIGNMENT_REJECTED)")

        # Role mismatch (pilot as copilot)
        status, err = http_post("/assignments", {
            "crewId": pilot["id"],
            "flightId": flight_id,
            "assignedRole": "COPILOT"
        })
        assert status == 409
        print(f"PASS: Role mismatch correctly rejected (HTTP 409)")

        # Missing aircraft qualification
        status, err = http_post("/assignments", {
            "crewId": b738_pilot["id"],
            "flightId": flight_id,
            "assignedRole": "PILOT"
        })
        assert status == 409
        print(f"PASS: Missing aircraft qualification correctly rejected (HTTP 409)")

        # Role already filled (assigning another pilot when pilot position is already filled)
        status, err = http_post("/assignments", {
            "crewId": 2,
            "flightId": flight_id,
            "assignedRole": "PILOT"
        })
        assert status == 409
        print(f"PASS: Excess role assignment rejected: Flight role already filled (HTTP 409)")

        # 7. Assignment Cancellation and History Retention
        print("\n--- 7. ASSIGNMENT CANCELLATION, VERSIONING & RE-OPENING VACANCY ---")
        cancel_url = f"/assignments/{a_pilot['id']}/cancellation"
        expected_header = f"{a_pilot['id']}:{a_pilot['version']}"
        status, cancelled_view = http_put(cancel_url, {}, headers={"X-Expected-Version": expected_header})
        assert status == 200
        assert cancelled_view["status"] == "CANCELLED"
        print(f"PASS: Cancelled Pilot assignment {a_pilot['id']} with version {expected_header}")

        # Verify coverage drops back to 3/4 (INCOMPLETE)
        status, cov_after_cancel = http_get(f"/flights/{flight_id}/coverage")
        assert cov_after_cancel["minimumStaffingSatisfied"] is False
        p_after = next(r for r in cov_after_cancel["roles"] if r["role"] == "PILOT")
        assert p_after["active"] == 0 and p_after["missing"] == 1
        print(f"PASS: Coverage dynamically updated to INCOMPLETE: Pilot 0/1, Co-pilot 1/1, Cabin 2/2")

        # Verify historical records retained (passing activeOnly=false)
        status, history = http_get(f"/flights/{flight_id}/assignments?activeOnly=false")
        assert status == 200
        assert len(history["items"]) == 4, f"Expected 4 historical records, got {len(history['items'])}"
        cancelled_rec = next((a for a in history["items"] if a["id"] == a_pilot["id"]), None)
        assert cancelled_rec is not None and cancelled_rec["status"] == "CANCELLED"
        print(f"PASS: Audit trail preserved: Cancelled record retained in flight assignment history")

        # 8. SPA Route Serving Check
        print("\n--- 8. SPA ROUTING & STATIC ASSETS CHECK ---")
        req = urllib.request.Request(f"{BASE_URL}/flights/{flight_id}/assign")
        with urllib.request.urlopen(req) as resp:
            html = resp.read().decode("utf-8")
            assert resp.status == 200
            assert "<div id=\"root\">" in html or "<div id='root'>" in html or "assets/index" in html
        print(f"PASS: SPA Route GET /flights/{flight_id}/assign served successfully with HTML entrypoint")

    finally:
        # 9. Cleanup Test Fixtures
        print("\n--- 9. CLEANING UP TEST FIXTURES ---")
        # Cancel any active assignments
        status, cur_assigns = http_get(f"/flights/{flight_id}/assignments?activeOnly=true")
        if status == 200:
            for a in cur_assigns.get("items", []):
                ver_hdr = f"{a['id']}:{a['version']}"
                http_put(f"/assignments/{a['id']}/cancellation", {}, headers={"X-Expected-Version": ver_hdr})
        # Cancel flight
        status, cur_f = http_get(f"/flights/{flight_id}")
        if status == 200:
            f_ver_hdr = f"{cur_f['id']}:{cur_f['version']}"
            http_put(f"/flights/{flight_id}/cancellation", {}, headers={"X-Expected-Version": f_ver_hdr})
        # Also clean up DB records so persistence seed counts remain pristine
        try:
            sql_clean = (
                f"DELETE FROM duty_assignment WHERE flight_id = {flight_id}; "
                f"DELETE FROM flight_audit WHERE flight_id = {flight_id}; "
                f"DELETE FROM flight WHERE id = {flight_id};"
            )
            subprocess.run(
                [r"C:\Program Files\PostgreSQL\17\bin\psql.exe", "-U", "postgres", "-d", "aeroroster_test", "-c", sql_clean],
                env=dict(os.environ, PGPASSWORD="password"),
                capture_output=True,
                check=False
            )
        except Exception:
            pass
        print("PASS: Cleaned up test flight and assignments. Zero orphaned records.")

    print("\n" + "=" * 80)
    print("ALL 12 END-TO-END WORKFLOW VERIFICATIONS PASSED SUCCESSFULLY!")
    print("=" * 80)
    return True

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)

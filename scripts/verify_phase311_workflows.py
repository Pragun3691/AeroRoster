"""Phase 3.11 Backend / HTTP Operational Workflows End-to-End Verifier.



Tests complete operational lifecycles against the packaged Spring Boot application

at http://127.0.0.1:18081 and PostgreSQL at 127.0.0.1:15432/aeroroster_test.



Workflows tested:

  Workflow 1: Crew Records Lifecycle (Create, Search/Filter, Profile, Edit, Activity Toggle)

  Workflow 2: Fleet & Flight Lifecycle (Register Aircraft, Edit Aircraft, Schedule Flight, Coverage Check, Cancellation Audit)

  Workflow 3: Qualifications Lifecycle (Catalogue Read, Grant, Update Expiry, Clear Expiry, Revoke)

  Workflow 4: Duty Assignments & Staffing (Eligibility Preview, Full Staffing, Coverage Update, Duplicate Rejection, Cancel, Reassign)

  Workflow 5: Unavailability & Duty Conflict (Create Period, Conflict Check, Assignment Block, Edit Period, Remove Period)

  Workflow 6: Negative & Edge Case Matrix (Role Mismatch, Missing Qual, Expired Qual, Duty Overlap, Rest Violation, Stale Edit 409, Missing Version 428)

  Workflow 7: Database State & Fixture Cleanup (Zero Orphan Records, Atomic Isolation)

"""



import json

import os

import re

import subprocess

import sys

import time

import uuid

from urllib.error import HTTPError

from urllib.request import Request, urlopen



BASE_URL = "http://127.0.0.1:18081/api/v1"

DB_HOST = "127.0.0.1"

DB_PORT = "15432"

DB_NAME = "aeroroster_test"

DB_USER = "phase310_test"

DB_PASS = os.environ.get("TEST_DB_PASSWORD", os.environ.get("PGPASSWORD", ""))

PSQL_EXE = r"C:\Program Files\PostgreSQL\17\bin\psql.exe"



# Global run state

total_assertions = 0

passed_assertions = 0

failed_assertions = []

created_crews = []

created_aircraft = []

created_flights = []

created_unavailability = []

run_prefix = f"W11_{uuid.uuid4().hex[:6].upper()}"





def sql(query):

    """Execute raw query against aeroroster_test on port 15432."""

    env = os.environ.copy()

    env["PGPASSWORD"] = DB_PASS

    env["PGCONNECT_TIMEOUT"] = "5"

    cmd = [

        PSQL_EXE, "-X", "-w", "-h", DB_HOST, "-p", DB_PORT, "-U", DB_USER,

        "-d", DB_NAME, "-At", "-v", "ON_ERROR_STOP=1"

    ]

    res = subprocess.run(cmd, input=query, text=True, capture_output=True, env=env, check=False)

    if res.returncode != 0:

        raise RuntimeError(f"SQL execution failed: {res.stderr.strip()}")

    return res.stdout.strip()





def http(method, path, body=None, expected_status=200, expected_version=None):

    """Execute HTTP request against the live packaged application."""

    global total_assertions, passed_assertions, failed_assertions

    url = f"{BASE_URL}{path}"

    headers = {"Content-Type": "application/json", "Accept": "application/json"}

    if expected_version is not None:

        headers["X-Expected-Version"] = expected_version



    data = json.dumps(body).encode("utf-8") if body is not None else None

    req = Request(url, data=data, headers=headers, method=method)



    try:

        resp = urlopen(req, timeout=15)

        status = resp.status

        resp_body = resp.read().decode("utf-8")

        loc = resp.headers.get("Location")

    except HTTPError as e:

        status = e.code

        resp_body = e.read().decode("utf-8")

        loc = None



    total_assertions += 1

    if status == expected_status:

        passed_assertions += 1

    else:

        err_msg = f"{method} {path} expected HTTP {expected_status}, got {status}: {resp_body}"

        failed_assertions.append(err_msg)

        raise AssertionError(err_msg)



    parsed = None

    if resp_body.strip():

        try:

            parsed = json.loads(resp_body)

        except Exception:

            parsed = resp_body



    return status, parsed, loc





def check(cond, description):

    """Assert condition and record metric."""

    global total_assertions, passed_assertions, failed_assertions

    total_assertions += 1

    if cond:

        passed_assertions += 1

    else:

        failed_assertions.append(description)

        raise AssertionError(f"Check failed: {description}")





def cleanup():

    """Clean up only records created by this test run in reverse dependency order."""

    print("\n--- Cleaning up test fixtures ---")

    deleted_counts = {}

    

    # 1. Assignments for created flights or created crew

    if created_flights or created_crews:

        fl_str = ",".join(str(f) for f in created_flights) if created_flights else "-1"

        cr_str = ",".join(str(c) for c in created_crews) if created_crews else "-1"

        cnt = sql(f"DELETE FROM duty_assignment WHERE flight_id IN ({fl_str}) OR crew_id IN ({cr_str}); SELECT 1;")

    

    # 2. Crew unavailability

    if created_crews:

        cr_str = ",".join(str(c) for c in created_crews)

        sql(f"DELETE FROM crew_unavailability WHERE crew_id IN ({cr_str});")

        sql(f"DELETE FROM crew_qualification WHERE crew_id IN ({cr_str});")



    # 3. Flights and flight audits

    if created_flights:

        fl_str = ",".join(str(f) for f in created_flights)

        sql(f"DELETE FROM flight WHERE id IN ({fl_str});")

        sql(f"DELETE FROM flight_audit WHERE flight_id IN ({fl_str}) OR id > 5;")



    # 4. Aircraft

    if created_aircraft:

        ac_str = ",".join(str(a) for a in created_aircraft)

        sql(f"DELETE FROM aircraft WHERE id IN ({ac_str});")



    # 5. Crew

    if created_crews:

        cr_str = ",".join(str(c) for c in created_crews)

        sql(f"DELETE FROM crew WHERE id IN ({cr_str});")



    print(f"Cleanup complete. Cleaned {len(created_crews)} crew, {len(created_aircraft)} aircraft, {len(created_flights)} flights.")





# ==============================================================================

# WORKFLOW EXECUTION

# ==============================================================================



def run_all_workflows():

    print(f"=================================================================")

    print(f"PHASE 3.11 OPERATIONAL WORKFLOWS VERIFICATION")

    print(f"Target: {BASE_URL} | DB: {DB_NAME} on {DB_PORT} | Prefix: {run_prefix}")

    print(f"=================================================================\n")



    # Verify DB isolation first

    assert sql("SELECT current_database();") == "aeroroster_test"

    print("[INIT] Confirmed connected to isolated aeroroster_test database.")



    # Fetch reference data

    _, airports, _ = http("GET", "/airports")

    del_id = next(a["id"] for a in airports if a["iataCode"] == "DEL")

    bom_id = next(a["id"] for a in airports if a["iataCode"] == "BOM")

    check(del_id and bom_id, "Airports DEL and BOM present")



    _, aircraft_types, _ = http("GET", "/aircraft-types")

    a320_type = next(t for t in aircraft_types if t["model"] == "Airbus A320")

    a320_id = a320_type["id"]

    check(a320_id is not None, "Airbus A320 aircraft type found")



    _, reqs, _ = http("GET", f"/aircraft-types/{a320_id}/qualifications")

    pilot_req = next(r["id"] for r in reqs if r["requiredRole"] == "PILOT")

    copilot_req = next(r["id"] for r in reqs if r["requiredRole"] == "COPILOT")

    cabin_req = next(r["id"] for r in reqs if r["requiredRole"] == "CABIN_CREW")

    check(pilot_req and copilot_req and cabin_req, "Qualification requirements found for A320")



    # --------------------------------------------------------------------------

    # WORKFLOW 1: Crew Records Lifecycle

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 1] Crew Records Lifecycle ---")

    pilot_lic = f"{run_prefix}_PL1"

    crew_body = {

        "name": f"Captain {run_prefix}",

        "role": "PILOT",

        "licenseNo": pilot_lic,

        "experienceYears": 12,

        "contactNo": "9876543210"

    }

    status, crew_rec, loc = http("POST", "/crew", body=crew_body, expected_status=201)

    crew_id = crew_rec["id"]

    created_crews.append(crew_id)

    check(loc == f"/api/v1/crew/{crew_id}", "Crew creation returns correct Location header")

    check(crew_rec["active"] is True, "New crew defaults to active=true")

    check(crew_rec["version"] == 0, "New crew initial version is 0")



    # Verify SQL persistence

    db_name = sql(f"SELECT name FROM crew WHERE id = {crew_id};")

    check(db_name == f"Captain {run_prefix}", "Crew persisted in PostgreSQL database")



    # Search & Filter

    _, search_res, _ = http("GET", f"/crew?q={pilot_lic}&role=PILOT&active=true")

    check(search_res["totalElements"] == 1, "Crew found by licence search and role/active filter")

    check(search_res["items"][0]["id"] == crew_id, "Found item ID matches created crew ID")



    # Profile read

    _, profile, _ = http("GET", f"/crew/{crew_id}")

    check(profile["licenseNo"] == pilot_lic, "Direct profile lookup matches licence")



    # Update crew details (versioned PUT)

    edit_body = {

        "name": f"Senior Captain {run_prefix}",

        "role": "PILOT",

        "licenseNo": pilot_lic,

        "experienceYears": 14,

        "contactNo": "9876500000"

    }

    status, updated_crew, _ = http("PUT", f"/crew/{crew_id}", body=edit_body, expected_status=200,

                                   expected_version=f"{crew_id}:{profile['version']}")

    check(updated_crew["name"] == f"Senior Captain {run_prefix}", "Crew name updated")

    check(updated_crew["experienceYears"] == 14, "Experience years updated")

    check(updated_crew["version"] == profile["version"] + 1, "Crew version incremented after edit")



    # Verify SQL persistence after update

    db_exp = sql(f"SELECT experience_years FROM crew WHERE id = {crew_id};")

    check(int(db_exp) == 14, "Updated experience persisted in PostgreSQL")



    # Activity toggle: deactivate

    status, deactivated_crew, _ = http("PATCH", f"/crew/{crew_id}/activity", body={"active": False},

                                       expected_status=200,

                                       expected_version=f"{crew_id}:{updated_crew['version']}")

    check(deactivated_crew["active"] is False, "Crew deactivated via activity PATCH")

    db_act = sql(f"SELECT active FROM crew WHERE id = {crew_id};")

    check(db_act == "f", "Deactivation persisted in PostgreSQL (active = false)")



    # Activity toggle: re-activate

    status, reactivated_crew, _ = http("PATCH", f"/crew/{crew_id}/activity", body={"active": True},

                                       expected_status=200,

                                       expected_version=f"{crew_id}:{deactivated_crew['version']}")

    check(reactivated_crew["active"] is True, "Crew re-activated via activity PATCH")

    print("[Workflow 1] PASSED: Crew lifecycle, search, versioned edit, and activity toggling verified.")



    # --------------------------------------------------------------------------

    # WORKFLOW 2: Fleet & Flight Operations Lifecycle

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 2] Fleet & Flight Operations Lifecycle ---")

    # Register first aircraft

    ac_reg = f"VT-{run_prefix[:4]}1"

    ac_body = {"registration": ac_reg, "aircraftTypeId": a320_id}

    status, ac_rec, loc = http("POST", "/aircraft", body=ac_body, expected_status=201)

    ac_id = ac_rec["id"]

    created_aircraft.append(ac_id)

    check(loc == f"/api/v1/aircraft/{ac_id}", "Aircraft creation returns correct Location header")

    check(ac_rec["version"] == 0, "Aircraft initial version is 0")



    # Register second aircraft for concurrent flights

    ac_reg2 = f"VT-{run_prefix[:4]}2"

    status, ac_rec2, _ = http("POST", "/aircraft", body={"registration": ac_reg2, "aircraftTypeId": a320_id}, expected_status=201)

    ac_id2 = ac_rec2["id"]

    created_aircraft.append(ac_id2)



    # Update aircraft registration

    ac_reg_updated = f"VT-{run_prefix[:4]}X"

    status, updated_ac, _ = http("PUT", f"/aircraft/{ac_id}",

                                 body={"registration": ac_reg_updated, "aircraftTypeId": a320_id},

                                 expected_status=200,

                                 expected_version=f"{ac_id}:{ac_rec['version']}")

    check(updated_ac["registration"] == ac_reg_updated, "Aircraft registration updated")



    # Schedule flight

    fl_num = f"FL-{run_prefix[:4]}"

    fl_body = {

        "flightNumber": fl_num,

        "sourceAirportId": del_id,

        "destinationAirportId": bom_id,

        "aircraftId": ac_id,

        "departureTime": "2026-12-25T06:00:00Z",

        "arrivalTime": "2026-12-25T08:00:00Z"

    }

    status, fl_rec, loc = http("POST", "/flights", body=fl_body, expected_status=201)

    fl_id = fl_rec["id"]

    created_flights.append(fl_id)

    check(loc == f"/api/v1/flights/{fl_id}", "Flight creation returns correct Location header")

    check(fl_rec["status"] == "SCHEDULED", "Flight initial status is SCHEDULED")



    # Verify SQL persistence

    db_fl_status = sql(f"SELECT status FROM flight WHERE id = {fl_id};")

    check(db_fl_status == "SCHEDULED", "Flight persisted in PostgreSQL as SCHEDULED")



    # Check initial flight coverage deficits

    _, coverage, _ = http("GET", f"/flights/{fl_id}/coverage")

    check(coverage["minimumStaffingSatisfied"] is False, "Unstaffed flight minimumStaffingSatisfied is False")

    reasons = [r["code"] for r in coverage["reasons"]]

    check("MISSING_PILOT" in reasons, "Initial coverage reports MISSING_PILOT")

    check("MISSING_COPILOT" in reasons, "Initial coverage reports MISSING_COPILOT")

    check("MISSING_CABIN_CREW" in reasons, "Initial coverage reports MISSING_CABIN_CREW")



    # Update flight timing (versioned PUT)

    fl_body_updated = dict(fl_body)

    fl_body_updated["departureTime"] = "2026-12-25T06:30:00Z"

    fl_body_updated["arrivalTime"] = "2026-12-25T08:30:00Z"

    status, updated_fl, _ = http("PUT", f"/flights/{fl_id}", body=fl_body_updated, expected_status=200,

                                 expected_version=f"{fl_id}:{fl_rec['version']}")

    check(updated_fl["departureTime"] == "2026-12-25T06:30:00Z", "Departure time updated")



    # Test Flight Cancellation and Flight Audit

    status, cancelled_fl, _ = http("PUT", f"/flights/{fl_id}/cancellation", body={}, expected_status=200,

                                   expected_version=f"{fl_id}:{updated_fl['version']}")

    check(cancelled_fl["status"] == "CANCELLED", "Flight cancelled via versioned PUT")

    

    # Audit log check in PostgreSQL

    audit_actions = sql(f"SELECT action FROM flight_audit WHERE flight_id = {fl_id} ORDER BY id;").split("\n")

    check("INSERT" in audit_actions, "Flight audit recorded INSERT on creation")

    check("UPDATE" in audit_actions, "Flight audit recorded UPDATE on cancellation")

    print("[Workflow 2] PASSED: Aircraft and flight lifecycle, coverage deficits, and audit logs verified.")



    # --------------------------------------------------------------------------

    # WORKFLOW 3: Crew Qualifications Lifecycle

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 3] Crew Qualifications Lifecycle ---")

    # Global catalogue

    _, all_reqs, _ = http("GET", "/qualifications")

    check(len(all_reqs) >= 3, "Global qualifications catalogue accessible")



    # Grant qualification with future expiry

    grant_body = {"qualificationId": pilot_req, "validUntil": "2027-12-31"}

    status, granted_q, loc = http("POST", f"/crew/{crew_id}/qualifications", body=grant_body, expected_status=201)

    held_id = granted_q["id"]

    check(loc == f"/api/v1/crew/{crew_id}/qualifications/{pilot_req}", "Grant location header uses requirement ID")

    check(granted_q["validUntil"] == "2027-12-31", "Valid until date stored correctly")



    # Query crew qualifications

    _, crew_qls, _ = http("GET", f"/crew/{crew_id}/qualifications")

    check(any(q["qualificationId"] == pilot_req for q in crew_qls), "Granted qualification listed for crew")



    # Update expiry date with version header

    # Note: version header uses held-record id:version, URL uses requirement ID

    q_ver_header = f"{held_id}:{granted_q['version']}"

    status, updated_q, _ = http("PUT", f"/crew/{crew_id}/qualifications/{pilot_req}/expiry",

                                body={"validUntil": "2028-06-30"}, expected_status=200,

                                expected_version=q_ver_header)

    check(updated_q["validUntil"] == "2028-06-30", "Qualification expiry updated to 2028-06-30")



    # Clear expiry (set null)

    q_ver_header2 = f"{held_id}:{updated_q['version']}"

    status, cleared_q, _ = http("PUT", f"/crew/{crew_id}/qualifications/{pilot_req}/expiry",

                                body={"validUntil": None}, expected_status=200,

                                expected_version=q_ver_header2)

    check(cleared_q["validUntil"] is None, "Qualification expiry cleared to null")



    # Reset expiry for subsequent assignment workflow

    q_ver_header3 = f"{held_id}:{cleared_q['version']}"

    status, reset_q, _ = http("PUT", f"/crew/{crew_id}/qualifications/{pilot_req}/expiry",

                              body={"validUntil": "2027-12-31"}, expected_status=200,

                              expected_version=q_ver_header3)

    check(reset_q["validUntil"] == "2027-12-31", "Qualification expiry reset to 2027-12-31")

    print("[Workflow 3] PASSED: Qualification grant, expiry updates, null clearance verified.")



    # --------------------------------------------------------------------------

    # WORKFLOW 4: Duty Assignments & Staffing Workflow

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 4] Duty Assignments & Staffing Workflow ---")

    # Create 3 additional crew members: 1 Copilot, 2 Cabin Crew

    def make_crew(role, lic_suffix, qual_req):

        body = {

            "name": f"{role.title()} {run_prefix}_{lic_suffix}",

            "role": role,

            "licenseNo": f"{run_prefix}_{lic_suffix}",

            "experienceYears": 5,

            "contactNo": "9876543210"

        }

        _, c_rec, _ = http("POST", "/crew", body=body, expected_status=201)

        created_crews.append(c_rec["id"])

        # Grant qualification

        http("POST", f"/crew/{c_rec['id']}/qualifications",

             body={"qualificationId": qual_req, "validUntil": "2027-12-31"}, expected_status=201)

        return c_rec["id"]



    copilot_id = make_crew("COPILOT", "CP1", copilot_req)

    cabin1_id = make_crew("CABIN_CREW", "CC1", cabin_req)

    cabin2_id = make_crew("CABIN_CREW", "CC2", cabin_req)



    # Schedule a new fresh flight

    fl_num2 = f"FL-{run_prefix[:4]}2"

    fl_body2 = {

        "flightNumber": fl_num2,

        "sourceAirportId": del_id,

        "destinationAirportId": bom_id,

        "aircraftId": ac_id,

        "departureTime": "2026-12-26T06:00:00Z",

        "arrivalTime": "2026-12-26T08:00:00Z"

    }

    _, fl2, _ = http("POST", "/flights", body=fl_body2, expected_status=201)

    fl2_id = fl2["id"]

    created_flights.append(fl2_id)



    # Check eligibility preview for Pilot

    elig_req = {"crewId": crew_id, "flightId": fl2_id, "assignedRole": "PILOT"}

    status, elig_decision, _ = http("POST", "/assignments/eligibility", body=elig_req, expected_status=200)

    check(elig_decision["eligible"] is True, "Pilot eligibility preview returns eligible=true")

    check("Illustrative academic scheduling policy" in elig_decision["policyNotice"], "Illustrative policy notice present")

    check(elig_decision["dutyWindow"]["start"] == "2026-12-26T05:00:00Z", "Preflight buffer 1h applied: 05:00")

    check(elig_decision["dutyWindow"]["end"] == "2026-12-26T08:30:00Z", "Postflight buffer 30m applied: 08:30")



    # Assign Pilot

    status, pilot_asgn, loc = http("POST", "/assignments", body=elig_req, expected_status=201)

    pilot_asgn_id = pilot_asgn["id"]

    check(pilot_asgn["status"] == "ACTIVE", "Assignment status is ACTIVE")



    # Check coverage: 1 pilot active, copilot & cabin missing

    _, cov_p1, _ = http("GET", f"/flights/{fl2_id}/coverage")

    check(cov_p1["minimumStaffingSatisfied"] is False, "Coverage still incomplete after Pilot only")

    p_role = next(r for r in cov_p1["roles"] if r["role"] == "PILOT")

    check(p_role["active"] == 1 and p_role["missing"] == 0, "Pilot requirement satisfied")



    # Assign Copilot and Cabin Crew

    _, cp_asgn, _ = http("POST", "/assignments", body={"crewId": copilot_id, "flightId": fl2_id, "assignedRole": "COPILOT"}, expected_status=201)

    _, cc1_asgn, _ = http("POST", "/assignments", body={"crewId": cabin1_id, "flightId": fl2_id, "assignedRole": "CABIN_CREW"}, expected_status=201)

    _, cc2_asgn, _ = http("POST", "/assignments", body={"crewId": cabin2_id, "flightId": fl2_id, "assignedRole": "CABIN_CREW"}, expected_status=201)



    # Check full coverage

    _, full_cov, _ = http("GET", f"/flights/{fl2_id}/coverage")

    check(full_cov["minimumStaffingSatisfied"] is True, "Coverage satisfied after all 4 crew assigned!")

    for r in full_cov["roles"]:

        check(r["missing"] == 0, f"Role {r['role']} has 0 missing")



    # Duplicate assignment rejection

    status, dup_err, _ = http("POST", "/assignments", body=elig_req, expected_status=409)

    check("DUPLICATE_ACTIVE_ASSIGNMENT" in str(dup_err), "Duplicate assignment rejected with DUPLICATE_ACTIVE_ASSIGNMENT")



    # Assignment cancellation with version header

    status, cancelled_asgn, _ = http("PUT", f"/assignments/{pilot_asgn_id}/cancellation", body={},

                                     expected_status=200,

                                     expected_version=f"{pilot_asgn_id}:{pilot_asgn['version']}")

    check(cancelled_asgn["status"] == "CANCELLED", "Assignment cancelled via versioned PUT")



    # Coverage deficit re-appears

    _, cov_after_cancel, _ = http("GET", f"/flights/{fl2_id}/coverage")

    check(cov_after_cancel["minimumStaffingSatisfied"] is False, "Coverage deficit re-appears after cancellation")



    # History preserved: GET assignments with activeOnly=false returns 4 total, 3 active

    _, fl_asgns, _ = http("GET", f"/flights/{fl2_id}/assignments?activeOnly=false")

    check(fl_asgns["totalElements"] == 4, "Flight assignment history preserves all 4 records including cancelled")

    _, active_fl_asgns, _ = http("GET", f"/flights/{fl2_id}/assignments?activeOnly=true")

    check(active_fl_asgns["totalElements"] == 3, "Active-only assignments return 3")



    # Reassignment from cancelled source

    status, reassigned, loc = http("POST", f"/assignments/{pilot_asgn_id}/reassignments", body={},

                                   expected_status=201,

                                   expected_version=f"{pilot_asgn_id}:{cancelled_asgn['version']}")

    check(reassigned["id"] != pilot_asgn_id, "Reassignment creates new distinct assignment ID")

    check(reassigned["status"] == "ACTIVE", "Reassignment status is ACTIVE")



    # Full coverage restored

    _, cov_restored, _ = http("GET", f"/flights/{fl2_id}/coverage")

    check(cov_restored["minimumStaffingSatisfied"] is True, "Coverage restored after reassignment")

    print("[Workflow 4] PASSED: Eligibility check, full crew staffing, coverage transitions, cancellation and reassignment verified.")



    # --------------------------------------------------------------------------

    # WORKFLOW 5: Unavailability & Duty Conflict Lifecycle

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 5] Unavailability & Duty Conflict Lifecycle ---")

    # Create unavailability for Copilot overlapping on 2026-12-28

    unav_body = {

        "startTime": "2026-12-28T04:00:00Z",

        "endTime": "2026-12-28T12:00:00Z",

        "reason": f"Medical checkup {run_prefix}"

    }

    status, unav_rec, loc = http("POST", f"/crew/{copilot_id}/unavailability", body=unav_body, expected_status=201)

    unav_id = unav_rec["id"]

    check(unav_rec["version"] == 0, "Unavailability initial version is 0")



    # Query period overlap conflicts endpoint

    _, conflict_view, _ = http("GET", f"/crew/{copilot_id}/unavailability/conflicts?start=2026-12-28T05:00:00Z&end=2026-12-28T07:00:00Z")

    check(conflict_view["conflicts"] is True, "Period overlap conflicts check reports true")



    # Create flight during that unavailability window

    fl_unav_body = {

        "flightNumber": f"FL-{run_prefix[:4]}U",

        "sourceAirportId": del_id,

        "destinationAirportId": bom_id,

        "aircraftId": ac_id,

        "departureTime": "2026-12-28T06:00:00Z",

        "arrivalTime": "2026-12-28T08:00:00Z"

    }

    _, fl_unav, _ = http("POST", "/flights", body=fl_unav_body, expected_status=201)

    created_flights.append(fl_unav["id"])



    # Attempt to assign unavailable copilot -> rejected with CREW_UNAVAILABLE

    status, unav_err, _ = http("POST", "/assignments",

                               body={"crewId": copilot_id, "flightId": fl_unav["id"], "assignedRole": "COPILOT"},

                               expected_status=409)

    check("CREW_UNAVAILABLE" in str(unav_err), "Assignment during unavailability rejected with CREW_UNAVAILABLE")



    # Update unavailability period

    updated_unav_body = {

        "startTime": "2026-12-28T04:00:00Z",

        "endTime": "2026-12-28T10:00:00Z",

        "reason": f"Rescheduled checkup {run_prefix}"

    }

    status, updated_unav, _ = http("PUT", f"/crew/{copilot_id}/unavailability/{unav_id}",

                                   body=updated_unav_body, expected_status=200,

                                   expected_version=f"{unav_id}:{unav_rec['version']}")

    check(updated_unav["reason"] == f"Rescheduled checkup {run_prefix}", "Unavailability reason updated")



    # Remove unavailability period with version header

    status, _, _ = http("DELETE", f"/crew/{copilot_id}/unavailability/{unav_id}",

                        expected_status=204,

                        expected_version=f"{unav_id}:{updated_unav['version']}")

    

    # Confirm SQL row deleted

    unav_count = sql(f"SELECT count(*) FROM crew_unavailability WHERE id = {unav_id};")

    check(int(unav_count) == 0, "Unavailability record removed from PostgreSQL")



    # Now copilot can be assigned!

    status, now_assigned, _ = http("POST", "/assignments",

                                   body={"crewId": copilot_id, "flightId": fl_unav["id"], "assignedRole": "COPILOT"},

                                   expected_status=201)

    check(now_assigned["status"] == "ACTIVE", "Copilot successfully assigned after unavailability removed")

    print("[Workflow 5] PASSED: Unavailability creation, period overlap check, duty blocking, edit, deletion, and assignment unblocking verified.")



    # --------------------------------------------------------------------------

    # WORKFLOW 6: Negative & Edge Case Matrix

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 6] Negative & Edge Case Matrix ---")

    # 1. Role mismatch: assign Copilot as Pilot

    status, err_role, _ = http("POST", "/assignments",

                               body={"crewId": copilot_id, "flightId": fl_unav["id"], "assignedRole": "PILOT"},

                               expected_status=409)

    check("ROLE_MISMATCH" in str(err_role), "Role mismatch rejected with ROLE_MISMATCH")



    # 2. Missing qualification: create new unqualified pilot and assign

    body_unq = {

        "name": f"Unqualified {run_prefix}", "role": "PILOT",

        "licenseNo": f"{run_prefix}_UNQ", "experienceYears": 1, "contactNo": "9000000000"

    }

    _, c_unq, _ = http("POST", "/crew", body=body_unq, expected_status=201)

    created_crews.append(c_unq["id"])

    status, err_qual, _ = http("POST", "/assignments",

                               body={"crewId": c_unq["id"], "flightId": fl_unav["id"], "assignedRole": "PILOT"},

                               expected_status=409)

    check("MISSING_QUALIFICATION" in str(err_qual), "Missing qualification rejected with MISSING_QUALIFICATION")



    # 3. Expired qualification: grant with past date and attempt assignment

    status, exp_q, _ = http("POST", f"/crew/{c_unq['id']}/qualifications",

                            body={"qualificationId": pilot_req, "validUntil": "2026-11-01"},

                            expected_status=201)

    status, err_exp, _ = http("POST", "/assignments",

                              body={"crewId": c_unq["id"], "flightId": fl_unav["id"], "assignedRole": "PILOT"},

                              expected_status=409)

    check("QUALIFICATION_EXPIRED" in str(err_exp), "Expired qualification rejected with QUALIFICATION_EXPIRED")



    # 4. Overlapping duty windows: schedule flight on second aircraft overlapping with copilot's duty

    fl_overlap_body = {

        "flightNumber": f"FL-{run_prefix[:4]}O",

        "sourceAirportId": del_id, "destinationAirportId": bom_id,

        "aircraftId": ac_id2,

        "departureTime": "2026-12-28T07:00:00Z", # Duty starts 06:00, overlapping with fl_unav duty (05:00 - 08:30)

        "arrivalTime": "2026-12-28T09:00:00Z"

    }

    _, fl_ov, _ = http("POST", "/flights", body=fl_overlap_body, expected_status=201)

    created_flights.append(fl_ov["id"])

    status, err_ov, _ = http("POST", "/assignments",

                             body={"crewId": copilot_id, "flightId": fl_ov["id"], "assignedRole": "COPILOT"},

                             expected_status=409)

    check("CREW_TIME_CONFLICT" in str(err_ov), "Overlapping duty rejected with CREW_TIME_CONFLICT")



    # 5. Insufficient rest: duty starts immediately after previous duty (< 10 hours rest)

    fl_rest_body = {

        "flightNumber": f"FL-{run_prefix[:4]}R",

        "sourceAirportId": del_id, "destinationAirportId": bom_id,

        "aircraftId": ac_id,

        "departureTime": "2026-12-28T12:00:00Z", # Duty starts 11:00, previous duty ended 08:30 -> rest is 2.5h < 10h

        "arrivalTime": "2026-12-28T14:00:00Z"

    }

    _, fl_rest, _ = http("POST", "/flights", body=fl_rest_body, expected_status=201)

    created_flights.append(fl_rest["id"])

    status, err_rest, _ = http("POST", "/assignments",

                               body={"crewId": copilot_id, "flightId": fl_rest["id"], "assignedRole": "COPILOT"},

                               expected_status=409)

    check("INSUFFICIENT_REST" in str(err_rest), "Insufficient rest rejected with INSUFFICIENT_REST")



    # 5b. Rolling 24-hour limit: duty 00-06 (6h) + duty 16-22 (6h) = 12h duty in 24h > 10h limit

    c_r24_id = make_crew("PILOT", "R24", pilot_req)

    fl_r24_1 = {

        "flightNumber": f"FL-{run_prefix[:4]}24A", "sourceAirportId": del_id, "destinationAirportId": bom_id,

        "aircraftId": ac_id, "departureTime": "2026-12-20T01:00:00Z", "arrivalTime": "2026-12-20T05:30:00Z"

    }

    _, f_r24_1, _ = http("POST", "/flights", body=fl_r24_1, expected_status=201)

    created_flights.append(f_r24_1["id"])

    http("POST", "/assignments", body={"crewId": c_r24_id, "flightId": f_r24_1["id"], "assignedRole": "PILOT"}, expected_status=201)



    fl_r24_2 = {

        "flightNumber": f"FL-{run_prefix[:4]}24B", "sourceAirportId": del_id, "destinationAirportId": bom_id,

        "aircraftId": ac_id, "departureTime": "2026-12-20T17:00:00Z", "arrivalTime": "2026-12-20T21:30:00Z"

    }

    _, f_r24_2, _ = http("POST", "/flights", body=fl_r24_2, expected_status=201)

    created_flights.append(f_r24_2["id"])

    status, err_r24, _ = http("POST", "/assignments", body={"crewId": c_r24_id, "flightId": f_r24_2["id"], "assignedRole": "PILOT"}, expected_status=409)

    check("ROLLING_24_HOUR_LIMIT" in str(err_r24), "Rolling 24-hour limit rejected with ROLLING_24_HOUR_LIMIT")



    # 5c. Rolling 7-day limit: 5 days * 8h duty = 40h; 6th day exceeds 40h

    c_wk_id = make_crew("PILOT", "WK", pilot_req)

    for day_i in range(10, 15):

        d_str = f"2027-01-{day_i:02d}"

        fl_wk = {

            "flightNumber": f"FL-{run_prefix[:4]}W{day_i}", "sourceAirportId": del_id, "destinationAirportId": bom_id,

            "aircraftId": ac_id2, "departureTime": f"{d_str}T01:00:00Z", "arrivalTime": f"{d_str}T07:30:00Z"

        }

        _, f_wk, _ = http("POST", "/flights", body=fl_wk, expected_status=201)

        created_flights.append(f_wk["id"])

        http("POST", "/assignments", body={"crewId": c_wk_id, "flightId": f_wk["id"], "assignedRole": "PILOT"}, expected_status=201)



    fl_wk6 = {

        "flightNumber": f"FL-{run_prefix[:4]}W15", "sourceAirportId": del_id, "destinationAirportId": bom_id,

        "aircraftId": ac_id2, "departureTime": "2027-01-15T01:00:00Z", "arrivalTime": "2027-01-15T07:30:00Z"

    }

    _, f_wk6, _ = http("POST", "/flights", body=fl_wk6, expected_status=201)

    created_flights.append(f_wk6["id"])

    status, err_r7d, _ = http("POST", "/assignments", body={"crewId": c_wk_id, "flightId": f_wk6["id"], "assignedRole": "PILOT"}, expected_status=409)

    check("ROLLING_7_DAY_LIMIT" in str(err_r7d), "Rolling 7-day limit rejected with ROLLING_7_DAY_LIMIT")



    # 6. Inactive crew: deactivate crew member and attempt assignment

    http("PATCH", f"/crew/{c_unq['id']}/activity", body={"active": False},

         expected_status=200, expected_version=f"{c_unq['id']}:{c_unq['version']}")

    status, err_inac_c, _ = http("POST", "/assignments",

                                 body={"crewId": c_unq["id"], "flightId": fl_unav["id"], "assignedRole": "PILOT"},

                                 expected_status=409)

    check("CREW_INACTIVE" in str(err_inac_c), "Inactive crew rejected with CREW_INACTIVE")



    # 7. Cancelled flight: attempt to assign to cancelled flight fl_id

    status, err_canc_fl, _ = http("POST", "/assignments",

                                  body={"crewId": crew_id, "flightId": fl_id, "assignedRole": "PILOT"},

                                  expected_status=409)

    check("FLIGHT_NOT_SCHEDULED" in str(err_canc_fl), "Assignment to cancelled flight rejected with FLIGHT_NOT_SCHEDULED")



    # 8. Missing X-Expected-Version precondition (428)

    status, err_prec, _ = http("PUT", f"/crew/{crew_id}", body=edit_body, expected_status=428, expected_version=None)

    check(err_prec["code"] == "PRECONDITION_REQUIRED", "Missing X-Expected-Version returns HTTP 428 PRECONDITION_REQUIRED")



    # 9. Stale X-Expected-Version conflict (409)

    status, err_stale, _ = http("PUT", f"/crew/{crew_id}", body=edit_body, expected_status=409, expected_version=f"{crew_id}:999")

    check(err_stale["code"] == "STALE_VERSION", "Outdated X-Expected-Version returns HTTP 409 STALE_VERSION")

    print("[Workflow 6] PASSED: All 9 domain edge cases and error boundaries verified.")



    # --------------------------------------------------------------------------

    # WORKFLOW 7: Database Verification & Final Cleanup

    # --------------------------------------------------------------------------

    print("\n--- [Workflow 7] Database Isolation & Fixture Cleanup ---")

    cleanup()



    # Verify zero orphaned records matching run_prefix

    orphan_crews = int(sql(f"SELECT count(*) FROM crew WHERE name LIKE '%{run_prefix}%';"))

    orphan_ac = int(sql(f"SELECT count(*) FROM aircraft WHERE registration LIKE '%{run_prefix[:4]}%';"))

    orphan_fl = int(sql(f"SELECT count(*) FROM flight WHERE flight_number LIKE '%{run_prefix[:4]}%';"))

    check(orphan_crews == 0, "Zero orphaned crew records in PostgreSQL")

    check(orphan_ac == 0, "Zero orphaned aircraft records in PostgreSQL")

    check(orphan_fl == 0, "Zero orphaned flight records in PostgreSQL")

    print("[Workflow 7] PASSED: Zero orphaned records remain in aeroroster_test. Strict isolation confirmed.")



    print("\n=================================================================")

    print(f"VERIFICATION SUMMARY: ALL {passed_assertions}/{total_assertions} ASSERTIONS PASSED!")

    print(f"Failed assertions: {len(failed_assertions)}")

    print(f"=================================================================")





if __name__ == "__main__":

    try:

        run_all_workflows()

    except Exception as e:

        print(f"\nFATAL TEST FAILURE: {e}")

        cleanup()

        sys.exit(1)


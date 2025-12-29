import socket
import threading
import base64
import uuid
from datetime import datetime
from supabase import create_client, Client

SUPABASE_URL = "https://amlpjcmytnpirvcjmwwk.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFtbHBqY215dG5waXJ2Y2ptd3drIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY5MDkzMjMsImV4cCI6MjA4MjQ4NTMyM30.ynpmPh4yRv3vMeO6wvSIT95B9FWiAy9_03CCTf9WDUc"

HOST = "0.0.0.0"
PORT = 12345

supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

clients = []
clients_lock = threading.Lock()

# User socket mapping for targeted messaging
user_sockets = {}  # username -> socket
user_sockets_lock = threading.Lock()

# Heartbeat tracking for online presence
user_heartbeats = {}  # username -> last_heartbeat_timestamp
user_heartbeats_lock = threading.Lock()

# Report subscriptions for real-time updates
report_subscriptions = {}  # report_id -> set of usernames
subscriptions_lock = threading.Lock()

# Location sharing sessions (Sprint 5)
location_sessions = {}  # session_id -> {from_user, to_user, status, start_time}
location_sessions_lock = threading.Lock()


# ------------------ UTILITIES ------------------

def safe_send(sock, msg: str):
    try:
        sock.sendall((msg + "\n").encode("utf-8"))
    except:
        pass


def broadcast(msg: str):
    with clients_lock:
        for c in list(clients):
            safe_send(c, msg)


def send_to_user(username: str, msg: str):
    """Send message to specific user if online"""
    with user_sockets_lock:
        if username in user_sockets:
            safe_send(user_sockets[username], msg)
            return True
    return False


def register_user_socket(username: str, sock):
    """Register user's socket for targeted messaging"""
    with user_sockets_lock:
        user_sockets[username] = sock
    with user_heartbeats_lock:
        user_heartbeats[username] = datetime.now()
    print(f"[USER ONLINE] {username}")
    # Broadcast presence change to chat partners (in background to not block)
    threading.Thread(target=broadcast_presence_change, args=(username, "ONLINE"), daemon=True).start()


def unregister_user_socket(username: str):
    """Remove user's socket mapping"""
    with user_sockets_lock:
        if username in user_sockets:
            del user_sockets[username]
    with user_heartbeats_lock:
        user_heartbeats[username] = datetime.now()  # Update last seen
    print(f"[USER OFFLINE] {username}")
    # Broadcast presence change to chat partners (in background)
    threading.Thread(target=broadcast_presence_change, args=(username, "OFFLINE"), daemon=True).start()


def get_timestamp():
    """Get current timestamp in ISO format"""
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def upload_photo(base64_data: str, report_id: str) -> str:
    """Upload photo to Supabase Storage and return public URL"""
    if not base64_data or len(base64_data) < 100:
        return ""

    try:
        # Decode base64 to bytes
        image_bytes = base64.b64decode(base64_data)

        # Generate unique filename
        filename = f"report_{report_id}_{uuid.uuid4().hex[:8]}.jpg"

        # Upload to Supabase Storage
        result = supabase.storage.from_("report-photos").upload(
            path=filename,
            file=image_bytes,
            file_options={"content-type": "image/jpeg"}
        )

        # Get public URL
        public_url = supabase.storage.from_("report-photos").get_public_url(filename)
        print(f"[PHOTO] Uploaded: {filename}")
        return public_url

    except Exception as e:
        print(f"[ERROR] Photo upload failed: {e}")
        return ""


# ------------------ AUTH ------------------

def handle_auth(client_socket, message: str):
    """Handle authentication using Supabase Auth"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "AUTH_FAIL::FORMAT")
        return None

    command = parts[1]

    if command == "LOGIN":
        email = parts[2]
        password = parts[3]

        try:
            # Use Supabase Auth for login
            auth_response = supabase.auth.sign_in_with_password({
                "email": email,
                "password": password
            })

            if auth_response.user:
                user_id = auth_response.user.id
                user_email = auth_response.user.email

                # Get profile data from profiles table
                profile = supabase.table("profiles").select("*").eq("id", user_id).execute()
                phone = ""
                line = ""
                if profile.data:
                    phone = profile.data[0].get("phone") or ""
                    line = profile.data[0].get("line_id") or ""

                # Register user socket for targeted messaging
                register_user_socket(user_email, client_socket)
                safe_send(client_socket, f"AUTH_SUCCESS::{user_email}::{phone}::{line}")
                # Send unread counts
                send_unread_counts(client_socket, user_email)
                return user_email
            else:
                safe_send(client_socket, "AUTH_FAIL::Invalid Credentials")
                return None

        except Exception as e:
            error_msg = str(e)
            print(f"[AUTH ERROR] Login failed: {error_msg}")
            if "Invalid login credentials" in error_msg:
                safe_send(client_socket, "AUTH_FAIL::Invalid Credentials")
            else:
                safe_send(client_socket, f"AUTH_FAIL::{error_msg[:50]}")
            return None

    elif command == "SIGNUP":
        email = parts[2]
        password = parts[3]
        phone = parts[4] if len(parts) > 4 else ""
        line = parts[5] if len(parts) > 5 else ""

        try:
            # Use Supabase Auth for signup
            auth_response = supabase.auth.sign_up({
                "email": email,
                "password": password
            })

            if auth_response.user:
                user_id = auth_response.user.id
                user_email = auth_response.user.email

                # Create profile with additional data
                supabase.table("profiles").upsert({
                    "id": user_id,
                    "email": user_email,
                    "phone": phone,
                    "line_id": line
                }).execute()

                # Register user socket for targeted messaging
                register_user_socket(user_email, client_socket)
                safe_send(client_socket, f"AUTH_SUCCESS::{user_email}::{phone}::{line}")
                return user_email
            else:
                safe_send(client_socket, "AUTH_FAIL::Signup failed")
                return None

        except Exception as e:
            error_msg = str(e)
            print(f"[AUTH ERROR] Signup failed: {error_msg}")
            if "already registered" in error_msg.lower():
                safe_send(client_socket, "AUTH_FAIL::Email already registered")
            else:
                safe_send(client_socket, f"AUTH_FAIL::{error_msg[:50]}")
            return None

    elif command == "UPDATE":
        if len(parts) < 5:
            safe_send(client_socket, "UPDATE_FAIL::FORMAT")
            return None

        email = parts[2]
        phone = parts[3]
        line = parts[4]

        try:
            # Get user ID from email in profiles table
            profile = supabase.table("profiles").select("id").eq("email", email).execute()
            if profile.data:
                user_id = profile.data[0]["id"]
                supabase.table("profiles").update({
                    "phone": phone,
                    "line_id": line
                }).eq("id", user_id).execute()
                safe_send(client_socket, "UPDATE_SUCCESS")
            else:
                safe_send(client_socket, "UPDATE_FAIL::USER_NOT_FOUND")
        except Exception as e:
            print(f"[AUTH ERROR] Update failed: {e}")
            safe_send(client_socket, "UPDATE_FAIL::ERROR")
        return None

    return None


def send_unread_counts(client_socket, username: str):
    """Send unread notification and message counts to user"""
    try:
        # Count unread notifications
        notif_resp = supabase.table("notifications").select("id", count="exact").eq("username", username).eq("is_read", False).execute()
        notif_count = notif_resp.count if notif_resp.count else 0

        # Count unread messages
        msg_resp = supabase.table("messages").select("id", count="exact").eq("to_user", username).eq("is_read", False).execute()
        msg_count = msg_resp.count if msg_resp.count else 0

        safe_send(client_socket, f"UNREAD_COUNTS::{notif_count}::{msg_count}")
    except Exception as e:
        print(f"[ERROR] Failed to get unread counts: {e}")
        safe_send(client_socket, "UNREAD_COUNTS::0::0")


# ------------------ REPORTS ------------------

def send_all_reports(client_socket):
    try:
        resp = (
            supabase.table("reports")
            .select(
                "id, owner_id, is_lost, name, category, color, location, description, report_date, status, photo_url"
            )
            .order("id", desc=True)
            .execute()
        )

        for r in resp.data:
            report_type = "LOST" if r["is_lost"] else "FOUND"
            status = r.get("status") or "ACTIVE"
            photo_url = r.get("photo_url") or ""

            msg = (
                f"REPORT::{r['id']}::{r['owner_id']}::{report_type}::"
                f"{r['name']}::{r['category']}::{r['color']}::"
                f"{r['location']}::{r['description']}::{r['report_date']}::{status}::{photo_url}"
            )
            safe_send(client_socket, msg)

        safe_send(client_socket, "REPORTS_DONE")
        print("[SERVER] Sent all reports")

    except Exception as e:
        print("[SERVER ERROR] Failed to send reports:", e)


def handle_report_create(client_socket, message: str):
    parts = message.split("::")
    if len(parts) < 9:
        safe_send(client_socket, "REPORT_FAIL::FORMAT")
        return

    owner = parts[1]
    report_type = parts[2]
    name = parts[3]
    category = parts[4]
    color = parts[5]
    location = parts[6]
    description = parts[7]
    report_date = parts[8]
    photo_base64 = parts[9] if len(parts) > 9 else ""

    is_lost = report_type == "LOST"

    # Insert report first
    ins = supabase.table("reports").insert({
        "owner_id": owner,
        "is_lost": is_lost,
        "name": name,
        "category": category,
        "color": color,
        "location": location,
        "description": description,
        "report_date": report_date,
        "photo_url": ""
    }).execute()

    if ins.data:
        r = ins.data[0]
        report_id = str(r['id'])
        photo_url = ""

        # Upload photo if provided
        if photo_base64 and len(photo_base64) > 100:
            photo_url = upload_photo(photo_base64, report_id)
            if photo_url:
                # Update report with photo URL
                supabase.table("reports").update({"photo_url": photo_url}).eq("id", report_id).execute()

        msg = (
            f"REPORT::{report_id}::{owner}::{report_type}::"
            f"{name}::{category}::{color}::{location}::{description}::{report_date}::ACTIVE::{photo_url}"
        )
        broadcast(msg)
        safe_send(client_socket, f"REPORT_OK::{report_id}")

        # Check for potential matches
        check_for_matches(report_id, is_lost, name, category, color, location)
    else:
        safe_send(client_socket, "REPORT_FAIL::INSERT")


def handle_report_update(client_socket, message: str):
    parts = message.split("::")
    if len(parts) < 11:
        safe_send(client_socket, "UPDATE_FAIL::FORMAT")
        return

    rid = parts[2]
    owner = parts[3]
    report_type = parts[4]
    name = parts[5]
    category = parts[6]
    color = parts[7]
    location = parts[8]
    description = parts[9]
    report_date = parts[10]

    existing = supabase.table("reports").select("owner_id").eq("id", rid).execute()
    if not existing.data:
        safe_send(client_socket, "UPDATE_FAIL::NOT_FOUND")
        return
    if existing.data[0]["owner_id"] != owner:
        safe_send(client_socket, "UPDATE_FAIL::NOT_OWNER")
        return

    supabase.table("reports").update({
        "is_lost": report_type == "LOST",
        "name": name,
        "category": category,
        "color": color,
        "location": location,
        "description": description,
        "report_date": report_date
    }).eq("id", rid).execute()

    broadcast(f"REPORT_UPDATED::{rid}")
    safe_send(client_socket, "UPDATE_OK")


def send_user_reports(client_socket, username: str):
    """Send only reports belonging to a specific user"""
    try:
        resp = (
            supabase.table("reports")
            .select("id, owner_id, is_lost, name, category, color, location, description, report_date, status, photo_url")
            .eq("owner_id", username)
            .order("id", desc=True)
            .execute()
        )

        for r in resp.data:
            report_type = "LOST" if r["is_lost"] else "FOUND"
            status = r.get("status") or "ACTIVE"
            photo_url = r.get("photo_url") or ""

            msg = (
                f"MY_REPORT::{r['id']}::{r['owner_id']}::{report_type}::"
                f"{r['name']}::{r['category']}::{r['color']}::"
                f"{r['location']}::{r['description']}::{r['report_date']}::{status}::{photo_url}"
            )
            safe_send(client_socket, msg)

        safe_send(client_socket, "MY_REPORTS_DONE")
        print(f"[SERVER] Sent {len(resp.data)} reports for user: {username}")

    except Exception as e:
        print("[SERVER ERROR] Failed to send user reports:", e)
        safe_send(client_socket, "MY_REPORTS_DONE")


def handle_report_resolve(client_socket, message: str):
    """Mark a report as resolved"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "RESOLVE_FAIL::FORMAT")
        return

    rid = parts[2]
    owner = parts[3]

    existing = supabase.table("reports").select("owner_id").eq("id", rid).execute()
    if not existing.data:
        safe_send(client_socket, "RESOLVE_FAIL::NOT_FOUND")
        return
    if existing.data[0]["owner_id"] != owner:
        safe_send(client_socket, "RESOLVE_FAIL::NOT_OWNER")
        return

    supabase.table("reports").update({
        "status": "RESOLVED"
    }).eq("id", rid).execute()

    broadcast(f"REPORT_RESOLVED::{rid}")
    safe_send(client_socket, "RESOLVE_OK")
    print(f"[SERVER] Report {rid} marked as resolved")


def handle_report_delete(client_socket, message: str):
    """Delete a report"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "DELETE_FAIL::FORMAT")
        return

    rid = parts[2]
    owner = parts[3]

    existing = supabase.table("reports").select("owner_id").eq("id", rid).execute()
    if not existing.data:
        safe_send(client_socket, "DELETE_FAIL::NOT_FOUND")
        return
    if existing.data[0]["owner_id"] != owner:
        safe_send(client_socket, "DELETE_FAIL::NOT_OWNER")
        return

    supabase.table("reports").delete().eq("id", rid).execute()

    broadcast(f"REPORT_DELETED::{rid}")
    safe_send(client_socket, "DELETE_OK")
    print(f"[SERVER] Report {rid} deleted")


def handle_search(client_socket, message: str):
    """Search reports with filters"""
    parts = message.split("::")
    if len(parts) < 7:
        safe_send(client_socket, "SEARCH_FAIL::FORMAT")
        return

    query = parts[1] if parts[1] != "*" else None
    category = parts[2] if parts[2] != "*" else None
    report_type = parts[3] if parts[3] != "*" else None
    location = parts[4] if parts[4] != "*" else None
    date_from = parts[5] if parts[5] != "*" else None
    date_to = parts[6] if parts[6] != "*" else None

    try:
        q = supabase.table("reports").select(
            "id, owner_id, is_lost, name, category, color, location, description, report_date, status, photo_url"
        )

        # Apply filters
        if category:
            q = q.eq("category", category)

        if report_type == "LOST":
            q = q.eq("is_lost", True)
        elif report_type == "FOUND":
            q = q.eq("is_lost", False)

        if date_from:
            q = q.gte("report_date", date_from)

        if date_to:
            q = q.lte("report_date", date_to)

        q = q.order("id", desc=True)
        resp = q.execute()

        results = resp.data

        # Text search filter (name or description contains query)
        if query:
            query_lower = query.lower()
            results = [
                r for r in results
                if query_lower in (r.get("name") or "").lower()
                or query_lower in (r.get("description") or "").lower()
                or query_lower in (r.get("location") or "").lower()
            ]

        # Location partial match filter
        if location:
            location_lower = location.lower()
            results = [
                r for r in results
                if location_lower in (r.get("location") or "").lower()
            ]

        for r in results:
            report_type_str = "LOST" if r["is_lost"] else "FOUND"
            status = r.get("status") or "ACTIVE"
            photo_url = r.get("photo_url") or ""

            msg = (
                f"SEARCH_RESULT::{r['id']}::{r['owner_id']}::{report_type_str}::"
                f"{r['name']}::{r['category']}::{r['color']}::"
                f"{r['location']}::{r['description']}::{r['report_date']}::{status}::{photo_url}"
            )
            safe_send(client_socket, msg)

        safe_send(client_socket, f"SEARCH_DONE::{len(results)}")
        print(f"[SERVER] Search returned {len(results)} results")

    except Exception as e:
        print("[SERVER ERROR] Search failed:", e)
        safe_send(client_socket, "SEARCH_DONE::0")


# ------------------ SMART MATCHING ------------------

def calculate_match_score(lost, found):
    """Calculate match score between a lost and found report"""
    score = 0

    # Category match (40 points)
    if lost.get("category") and found.get("category"):
        if lost["category"].lower() == found["category"].lower():
            score += 40

    # Name similarity (30 points)
    lost_name = (lost.get("name") or "").lower()
    found_name = (found.get("name") or "").lower()
    if lost_name and found_name:
        # Simple word overlap
        lost_words = set(lost_name.split())
        found_words = set(found_name.split())
        common = lost_words & found_words
        if common:
            similarity = len(common) / max(len(lost_words), len(found_words))
            score += int(similarity * 30)

    # Color match (15 points)
    if lost.get("color") and found.get("color"):
        if lost["color"].lower() == found["color"].lower():
            score += 15

    # Location similarity (10 points)
    lost_loc = (lost.get("location") or "").lower()
    found_loc = (found.get("location") or "").lower()
    if lost_loc and found_loc:
        if lost_loc == found_loc:
            score += 10
        elif lost_loc in found_loc or found_loc in lost_loc:
            score += 5

    # Date proximity (5 points) - within 7 days
    try:
        from datetime import datetime
        lost_date = datetime.strptime(lost.get("report_date", ""), "%Y-%m-%d")
        found_date = datetime.strptime(found.get("report_date", ""), "%Y-%m-%d")
        days_diff = abs((lost_date - found_date).days)
        if days_diff <= 7:
            score += 5
        elif days_diff <= 14:
            score += 3
    except:
        pass

    return score


def get_confidence(score):
    """Convert score to confidence level"""
    if score >= 70:
        return "HIGH"
    elif score >= 40:
        return "MEDIUM"
    elif score >= 20:
        return "LOW"
    return None


def check_for_matches(report_id: str, is_lost: bool, name: str, category: str, color: str, location: str):
    """Check for potential matches when a new report is created"""
    try:
        # Get the new report's full data
        new_report = {
            "id": report_id,
            "name": name,
            "category": category,
            "color": color,
            "location": location,
            "report_date": datetime.now().strftime("%Y-%m-%d")
        }

        # Get opposite type reports (if lost, check found; if found, check lost)
        opposite_type = not is_lost
        resp = supabase.table("reports").select("*").eq("is_lost", opposite_type).eq("status", "ACTIVE").execute()

        matches_found = []
        for r in resp.data:
            if str(r["id"]) == report_id:
                continue

            if is_lost:
                score = calculate_match_score(new_report, r)
            else:
                score = calculate_match_score(r, new_report)

            confidence = get_confidence(score)
            if confidence:
                matches_found.append({
                    "lost_id": report_id if is_lost else str(r["id"]),
                    "found_id": str(r["id"]) if is_lost else report_id,
                    "score": score,
                    "confidence": confidence,
                    "other_report": r
                })

        # Save matches and notify users
        for match in matches_found:
            # Save to matches table
            try:
                supabase.table("matches").insert({
                    "lost_report_id": match["lost_id"],
                    "found_report_id": match["found_id"],
                    "score": match["score"],
                    "confidence": match["confidence"],
                    "status": "PENDING"
                }).execute()
            except Exception as e:
                print(f"[ERROR] Failed to save match: {e}")
                continue

            # Notify the other user
            other_owner = match["other_report"]["owner_id"]
            other_name = match["other_report"]["name"]
            create_notification(
                other_owner,
                "MATCH",
                f"Potential match found!",
                f"Your item '{other_name}' may match a new {('lost' if is_lost else 'found')} report.",
                match["lost_id"] if is_lost else match["found_id"]
            )

        if matches_found:
            print(f"[MATCH] Found {len(matches_found)} potential matches for report {report_id}")

    except Exception as e:
        print(f"[ERROR] Match checking failed: {e}")


def handle_get_matches(client_socket, message: str):
    """Get all pending matches for a user"""
    parts = message.split("::")
    if len(parts) < 3:
        safe_send(client_socket, "MATCHES_DONE")
        return

    username = parts[2]

    try:
        # Get user's report IDs
        user_reports = supabase.table("reports").select("id").eq("owner_id", username).execute()
        report_ids = [str(r["id"]) for r in user_reports.data]

        if not report_ids:
            safe_send(client_socket, "MATCHES_DONE")
            return

        # Get matches involving user's reports
        matches = []
        for rid in report_ids:
            lost_matches = supabase.table("matches").select("*").eq("lost_report_id", rid).eq("status", "PENDING").execute()
            found_matches = supabase.table("matches").select("*").eq("found_report_id", rid).eq("status", "PENDING").execute()
            matches.extend(lost_matches.data)
            matches.extend(found_matches.data)

        # Get report details for each match
        for m in matches:
            lost_report = supabase.table("reports").select("*").eq("id", m["lost_report_id"]).execute()
            found_report = supabase.table("reports").select("*").eq("id", m["found_report_id"]).execute()

            if lost_report.data and found_report.data:
                lr = lost_report.data[0]
                fr = found_report.data[0]

                msg = (
                    f"MATCH::{m['id']}::{m['score']}::{m['confidence']}::"
                    f"{lr['id']}::{lr['owner_id']}::{lr['name']}::{lr.get('photo_url') or ''}::"
                    f"{fr['id']}::{fr['owner_id']}::{fr['name']}::{fr.get('photo_url') or ''}"
                )
                safe_send(client_socket, msg)

        safe_send(client_socket, "MATCHES_DONE")
        print(f"[SERVER] Sent {len(matches)} matches for {username}")

    except Exception as e:
        print(f"[ERROR] Failed to get matches: {e}")
        safe_send(client_socket, "MATCHES_DONE")


def handle_match_confirm(client_socket, message: str):
    """Confirm a match"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "MATCH_FAIL::FORMAT")
        return

    match_id = parts[2]
    username = parts[3]

    try:
        # Update match status
        supabase.table("matches").update({"status": "CONFIRMED"}).eq("id", match_id).execute()

        # Get match details
        match_data = supabase.table("matches").select("*").eq("id", match_id).execute()
        if match_data.data:
            m = match_data.data[0]

            # Get both report owners
            lost_report = supabase.table("reports").select("owner_id, name").eq("id", m["lost_report_id"]).execute()
            found_report = supabase.table("reports").select("owner_id, name").eq("id", m["found_report_id"]).execute()

            if lost_report.data and found_report.data:
                lost_owner = lost_report.data[0]["owner_id"]
                found_owner = found_report.data[0]["owner_id"]

                # Notify both users
                create_notification(lost_owner, "MATCH", "Match confirmed!",
                    f"Your item may have been found! Start chatting to arrange pickup.", m["found_report_id"])
                create_notification(found_owner, "MATCH", "Match confirmed!",
                    f"Someone confirmed your found item matches theirs! Start chatting.", m["lost_report_id"])

                # Send confirmation to both if online
                confirm_msg = f"MATCH::CONFIRMED::{match_id}::{m['lost_report_id']}::{m['found_report_id']}"
                send_to_user(lost_owner, confirm_msg)
                send_to_user(found_owner, confirm_msg)

        safe_send(client_socket, f"MATCH_CONFIRMED::{match_id}")

    except Exception as e:
        print(f"[ERROR] Failed to confirm match: {e}")
        safe_send(client_socket, "MATCH_FAIL::ERROR")


def handle_match_dismiss(client_socket, message: str):
    """Dismiss a match"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "MATCH_FAIL::FORMAT")
        return

    match_id = parts[2]
    username = parts[3]

    try:
        supabase.table("matches").update({"status": "DISMISSED"}).eq("id", match_id).execute()
        safe_send(client_socket, f"MATCH_DISMISSED::{match_id}")
    except Exception as e:
        print(f"[ERROR] Failed to dismiss match: {e}")
        safe_send(client_socket, "MATCH_FAIL::ERROR")


# ------------------ NOTIFICATIONS ------------------

def create_notification(username: str, notif_type: str, title: str, message: str, related_id: str = None):
    """Create and send notification to user"""
    try:
        timestamp = get_timestamp()

        # Save to database
        ins = supabase.table("notifications").insert({
            "username": username,
            "type": notif_type,
            "title": title,
            "message": message,
            "related_id": related_id,
            "is_read": False,
            "created_at": timestamp
        }).execute()

        if ins.data:
            notif = ins.data[0]
            notif_msg = f"NOTIFICATION::{notif['id']}::{notif_type}::{title}::{message}::{timestamp}::unread"

            # Send to user if online
            if send_to_user(username, notif_msg):
                print(f"[NOTIFICATION] Sent to {username}: {title}")
            else:
                print(f"[NOTIFICATION] Stored for {username} (offline): {title}")

    except Exception as e:
        print(f"[ERROR] Failed to create notification: {e}")


def handle_get_notifications(client_socket, message: str):
    """Get all notifications for a user"""
    parts = message.split("::")
    if len(parts) < 3:
        safe_send(client_socket, "NOTIFICATIONS_DONE")
        return

    username = parts[2]

    try:
        resp = supabase.table("notifications").select("*").eq("username", username).order("created_at", desc=True).limit(50).execute()

        for n in resp.data:
            read_status = "read" if n.get("is_read") else "unread"
            msg = f"NOTIFICATION::{n['id']}::{n['type']}::{n['title']}::{n['message']}::{n['created_at']}::{read_status}"
            safe_send(client_socket, msg)

        safe_send(client_socket, "NOTIFICATIONS_DONE")
        print(f"[SERVER] Sent {len(resp.data)} notifications to {username}")

    except Exception as e:
        print(f"[ERROR] Failed to get notifications: {e}")
        safe_send(client_socket, "NOTIFICATIONS_DONE")


def handle_mark_notification_read(client_socket, message: str):
    """Mark notification as read"""
    parts = message.split("::")
    if len(parts) < 3:
        return

    notif_id = parts[2]

    try:
        supabase.table("notifications").update({"is_read": True}).eq("id", notif_id).execute()
        safe_send(client_socket, f"NOTIFICATION_READ_OK::{notif_id}")
    except Exception as e:
        print(f"[ERROR] Failed to mark notification read: {e}")


def handle_mark_all_notifications_read(client_socket, message: str):
    """Mark all notifications as read for a user"""
    parts = message.split("::")
    if len(parts) < 3:
        return

    username = parts[2]

    try:
        supabase.table("notifications").update({"is_read": True}).eq("username", username).execute()
        safe_send(client_socket, "NOTIFICATIONS_ALL_READ_OK")
    except Exception as e:
        print(f"[ERROR] Failed to mark all notifications read: {e}")


def handle_get_unread_count(client_socket, message: str):
    """Get count of unread notifications for a user"""
    parts = message.split("::")
    if len(parts) < 3:
        safe_send(client_socket, "NOTIFICATIONS::UNREAD_COUNT::0")
        return

    username = parts[2]

    try:
        resp = supabase.table("notifications").select("id", count="exact").eq("username", username).eq("is_read", False).execute()
        count = resp.count if resp.count is not None else 0
        safe_send(client_socket, f"NOTIFICATIONS::UNREAD_COUNT::{count}")
    except Exception as e:
        print(f"[ERROR] Failed to get unread count: {e}")
        safe_send(client_socket, "NOTIFICATIONS::UNREAD_COUNT::0")


# ------------------ CHAT ------------------

def handle_chat_send(client_socket, message: str):
    """Handle sending a chat message"""
    parts = message.split("::")
    if len(parts) < 6:
        safe_send(client_socket, "CHAT_FAIL::FORMAT")
        return

    from_user = parts[2]
    to_user = parts[3]
    report_id = parts[4]
    msg_text = parts[5]
    timestamp = get_timestamp()

    try:
        # Save message to database
        ins = supabase.table("messages").insert({
            "from_user": from_user,
            "to_user": to_user,
            "report_id": report_id if report_id != "*" else None,
            "message_text": msg_text,
            "timestamp": timestamp,
            "is_read": False
        }).execute()

        if ins.data:
            msg_id = ins.data[0]["id"]

            # Confirm to sender
            safe_send(client_socket, f"CHAT_SENT::{msg_id}")

            # Send to recipient
            chat_msg = f"CHAT::RECEIVE::{from_user}::{to_user}::{report_id}::{msg_text}::{timestamp}::{msg_id}"
            if send_to_user(to_user, chat_msg):
                print(f"[CHAT] {from_user} -> {to_user}: {msg_text[:30]}...")
            else:
                # Create notification for offline user
                create_notification(to_user, "MESSAGE", f"New message from {from_user}", msg_text[:50], str(msg_id))
        else:
            safe_send(client_socket, "CHAT_FAIL::INSERT")

    except Exception as e:
        print(f"[ERROR] Failed to send chat: {e}")
        safe_send(client_socket, "CHAT_FAIL::ERROR")


def handle_chat_history(client_socket, message: str):
    """Get chat history between two users"""
    parts = message.split("::")
    if len(parts) < 5:
        safe_send(client_socket, "CHAT::HISTORY_DONE")
        return

    user1 = parts[2]
    user2 = parts[3]
    report_id = parts[4] if parts[4] != "*" else None

    try:
        # Get messages between these users
        query = supabase.table("messages").select("*")

        # Messages in either direction
        if report_id:
            query = query.eq("report_id", report_id)

        query = query.or_(f"and(from_user.eq.{user1},to_user.eq.{user2}),and(from_user.eq.{user2},to_user.eq.{user1})")
        query = query.order("timestamp", desc=False).limit(100)

        resp = query.execute()

        for m in resp.data:
            read_status = "read" if m.get("is_read") else "unread"
            msg = f"CHAT::MSG::{m['from_user']}::{m['to_user']}::{m.get('report_id') or '*'}::{m['message_text']}::{m['timestamp']}::{m['id']}::{read_status}"
            safe_send(client_socket, msg)

        safe_send(client_socket, "CHAT::HISTORY_DONE")
        print(f"[SERVER] Sent {len(resp.data)} messages for {user1}<->{user2}")

    except Exception as e:
        print(f"[ERROR] Failed to get chat history: {e}")
        safe_send(client_socket, "CHAT::HISTORY_DONE")


def handle_chat_typing(client_socket, message: str):
    """Relay typing indicator to recipient"""
    parts = message.split("::")
    if len(parts) < 5:
        return

    from_user = parts[2]
    to_user = parts[3]
    report_id = parts[4]

    # Relay typing indicator to recipient
    typing_msg = f"CHAT::TYPING::{from_user}::{to_user}::{report_id}"
    send_to_user(to_user, typing_msg)


def handle_chat_read(client_socket, message: str):
    """Mark messages as read"""
    parts = message.split("::")
    if len(parts) < 5:
        return

    from_user = parts[2]  # The reader
    to_user = parts[3]    # The sender of messages being read
    report_id = parts[4] if len(parts) > 4 and parts[4] != "*" else None

    try:
        # Mark all messages from to_user to from_user as read
        query = supabase.table("messages").update({"is_read": True}).eq("from_user", to_user).eq("to_user", from_user)

        if report_id:
            query = query.eq("report_id", report_id)

        query.execute()

        # Notify the sender that messages were read
        read_receipt = f"CHAT::READ_RECEIPT::{from_user}::{to_user}::{report_id or '*'}::{get_timestamp()}"
        send_to_user(to_user, read_receipt)

    except Exception as e:
        print(f"[ERROR] Failed to mark messages read: {e}")


def handle_get_conversations(client_socket, message: str):
    """Get list of conversations for a user"""
    parts = message.split("::")
    if len(parts) < 3:
        safe_send(client_socket, "CHAT::CONVERSATIONS_DONE")
        return

    username = parts[2]

    try:
        # Get all messages involving this user
        resp = supabase.table("messages").select("*").or_(
            f"from_user.eq.{username},to_user.eq.{username}"
        ).order("timestamp", desc=True).execute()

        # Group by conversation (other user + report)
        conversations = {}
        for m in resp.data:
            other_user = m["to_user"] if m["from_user"] == username else m["from_user"]
            report_id = m.get("report_id") or "*"
            conv_key = f"{other_user}::{report_id}"

            if conv_key not in conversations:
                # Count unread for this conversation
                unread = 0
                for msg in resp.data:
                    if msg["to_user"] == username and not msg.get("is_read"):
                        msg_other = msg["from_user"]
                        msg_report = msg.get("report_id") or "*"
                        if msg_other == other_user and msg_report == report_id:
                            unread += 1

                conversations[conv_key] = {
                    "other_user": other_user,
                    "report_id": report_id,
                    "last_message": m["message_text"],
                    "timestamp": m["timestamp"],
                    "unread": unread
                }

        # Send conversations
        for conv in conversations.values():
            msg = f"CHAT::CONV::{conv['other_user']}::{conv['report_id']}::{conv['last_message'][:50]}::{conv['timestamp']}::{conv['unread']}"
            safe_send(client_socket, msg)

        safe_send(client_socket, "CHAT::CONVERSATIONS_DONE")
        print(f"[SERVER] Sent {len(conversations)} conversations for {username}")

    except Exception as e:
        print(f"[ERROR] Failed to get conversations: {e}")
        safe_send(client_socket, "CHAT::CONVERSATIONS_DONE")


# ------------------ ONLINE PRESENCE ------------------

def update_heartbeat(username: str):
    """Update user's last heartbeat timestamp"""
    with user_heartbeats_lock:
        user_heartbeats[username] = datetime.now()


def get_last_seen(username: str):
    """Get user's last seen timestamp"""
    with user_heartbeats_lock:
        if username in user_heartbeats:
            return user_heartbeats[username].strftime("%Y-%m-%d %H:%M:%S")
    return None


def is_user_online(username: str) -> bool:
    """Check if user is online (has active socket)"""
    with user_sockets_lock:
        return username in user_sockets


def get_user_chat_partners(username: str) -> list:
    """Get list of users who have chatted with this user"""
    try:
        resp = supabase.table("messages").select("from_user, to_user").or_(
            f"from_user.eq.{username},to_user.eq.{username}"
        ).execute()

        partners = set()
        for m in resp.data:
            if m["from_user"] == username:
                partners.add(m["to_user"])
            else:
                partners.add(m["from_user"])
        return list(partners)
    except:
        return []


def broadcast_presence_change(username: str, status: str):
    """Broadcast presence change to relevant users (chat partners)"""
    partners = get_user_chat_partners(username)
    last_seen = get_last_seen(username) or get_timestamp()

    presence_msg = f"PRESENCE::{username}::{status}::{last_seen}"

    for partner in partners:
        send_to_user(partner, presence_msg)


def handle_heartbeat(client_socket, message: str, current_user: str):
    """Handle heartbeat from client"""
    parts = message.split("::")
    if len(parts) < 2:
        return

    username = parts[1] if len(parts) > 1 else current_user
    if username:
        update_heartbeat(username)
        safe_send(client_socket, "HEARTBEAT_ACK")


def handle_presence_check(client_socket, message: str):
    """Check if a user is online"""
    parts = message.split("::")
    if len(parts) < 3:
        return

    username = parts[2]
    is_online = is_user_online(username)
    last_seen = get_last_seen(username) or "unknown"

    status = "ONLINE" if is_online else "OFFLINE"
    safe_send(client_socket, f"PRESENCE::{username}::{status}::{last_seen}")


# ------------------ COMMENTS SYSTEM ------------------

def subscribe_to_report(report_id: str, username: str):
    """Subscribe user to report updates"""
    with subscriptions_lock:
        if report_id not in report_subscriptions:
            report_subscriptions[report_id] = set()
        report_subscriptions[report_id].add(username)
    print(f"[SUBSCRIBE] {username} subscribed to report {report_id}")


def unsubscribe_from_report(report_id: str, username: str):
    """Unsubscribe user from report updates"""
    with subscriptions_lock:
        if report_id in report_subscriptions:
            report_subscriptions[report_id].discard(username)
            if not report_subscriptions[report_id]:
                del report_subscriptions[report_id]
    print(f"[UNSUBSCRIBE] {username} unsubscribed from report {report_id}")


def notify_report_subscribers(report_id: str, message: str, exclude_user: str = None):
    """Send message to all users subscribed to a report"""
    with subscriptions_lock:
        subscribers = report_subscriptions.get(report_id, set()).copy()

    for username in subscribers:
        if username != exclude_user:
            send_to_user(username, message)


def handle_subscribe(client_socket, message: str):
    """Handle subscription to report updates"""
    parts = message.split("::")
    if len(parts) < 4:
        return

    report_id = parts[2]
    username = parts[3]

    subscribe_to_report(report_id, username)
    safe_send(client_socket, f"SUBSCRIBED::REPORT::{report_id}")


def handle_unsubscribe(client_socket, message: str):
    """Handle unsubscription from report updates"""
    parts = message.split("::")
    if len(parts) < 4:
        return

    report_id = parts[2]
    username = parts[3]

    unsubscribe_from_report(report_id, username)
    safe_send(client_socket, f"UNSUBSCRIBED::REPORT::{report_id}")


def handle_get_comments(client_socket, message: str):
    """Get all comments for a report"""
    parts = message.split("::")
    if len(parts) < 3:
        safe_send(client_socket, "COMMENTS_DONE::0::0")
        return

    report_id = parts[2]

    try:
        resp = supabase.table("comments").select("*").eq("report_id", report_id).order("created_at", desc=False).execute()

        for c in resp.data:
            msg = f"COMMENT::{c['id']}::{c['report_id']}::{c['username']}::{c['comment_text']}::{c['created_at']}"
            safe_send(client_socket, msg)

        safe_send(client_socket, f"COMMENTS_DONE::{report_id}::{len(resp.data)}")
        print(f"[SERVER] Sent {len(resp.data)} comments for report {report_id}")

    except Exception as e:
        print(f"[ERROR] Failed to get comments: {e}")
        safe_send(client_socket, f"COMMENTS_DONE::{report_id}::0")


def handle_post_comment(client_socket, message: str):
    """Post a new comment on a report"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "COMMENT_FAIL::FORMAT")
        return

    report_id = parts[2]
    username = parts[3]
    comment_text = parts[4] if len(parts) > 4 else ""

    if not comment_text.strip():
        safe_send(client_socket, "COMMENT_FAIL::EMPTY")
        return

    timestamp = get_timestamp()

    try:
        # Get report owner for notification
        report_resp = supabase.table("reports").select("owner_id, name").eq("id", report_id).execute()
        report_owner = report_resp.data[0]["owner_id"] if report_resp.data else None
        report_name = report_resp.data[0]["name"] if report_resp.data else "item"

        # Insert comment
        ins = supabase.table("comments").insert({
            "report_id": report_id,
            "username": username,
            "comment_text": comment_text,
            "created_at": timestamp
        }).execute()

        if ins.data:
            comment = ins.data[0]
            comment_id = comment["id"]

            # Confirm to sender
            safe_send(client_socket, f"COMMENT_POSTED::{comment_id}")

            # Broadcast to subscribers (include poster - client handles duplicates)
            new_comment_msg = f"COMMENT::NEW::{report_id}::{comment_id}::{username}::{comment_text}::{timestamp}"
            notify_report_subscribers(report_id, new_comment_msg)

            # Notify report owner (if not the commenter)
            if report_owner and report_owner != username:
                create_notification(
                    report_owner,
                    "COMMENT",
                    f"New comment on '{report_name}'",
                    f"{username}: {comment_text[:50]}{'...' if len(comment_text) > 50 else ''}",
                    report_id
                )

            print(f"[COMMENT] {username} commented on report {report_id}")
        else:
            safe_send(client_socket, "COMMENT_FAIL::INSERT")

    except Exception as e:
        print(f"[ERROR] Failed to post comment: {e}")
        safe_send(client_socket, "COMMENT_FAIL::ERROR")


def handle_delete_comment(client_socket, message: str):
    """Delete a comment (only by comment owner)"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "COMMENT_DELETE_FAIL::FORMAT")
        return

    comment_id = parts[2]
    username = parts[3]

    try:
        # Verify ownership
        existing = supabase.table("comments").select("username, report_id").eq("id", comment_id).execute()
        if not existing.data:
            safe_send(client_socket, "COMMENT_DELETE_FAIL::NOT_FOUND")
            return

        if existing.data[0]["username"] != username:
            safe_send(client_socket, "COMMENT_DELETE_FAIL::NOT_OWNER")
            return

        report_id = existing.data[0]["report_id"]

        # Delete comment
        supabase.table("comments").delete().eq("id", comment_id).execute()

        safe_send(client_socket, f"COMMENT_DELETE_OK::{comment_id}")

        # Notify subscribers
        delete_msg = f"COMMENT::DELETED::{report_id}::{comment_id}"
        notify_report_subscribers(report_id, delete_msg)

        print(f"[COMMENT] Comment {comment_id} deleted by {username}")

    except Exception as e:
        print(f"[ERROR] Failed to delete comment: {e}")
        safe_send(client_socket, "COMMENT_DELETE_FAIL::ERROR")


# ------------------ CLAIM & VERIFICATION ------------------

def handle_claim_submit(client_socket, message: str):
    """Submit a claim for a found item"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIM_FAIL::FORMAT")
        return

    report_id = parts[2]
    claimer = parts[3]
    proof_description = parts[4] if len(parts) > 4 else ""

    timestamp = get_timestamp()

    try:
        # Check if report exists and is a FOUND item
        report_resp = supabase.table("reports").select("owner_id, is_lost, name").eq("id", report_id).execute()
        if not report_resp.data:
            safe_send(client_socket, "CLAIM_FAIL::REPORT_NOT_FOUND")
            return

        report = report_resp.data[0]
        if report["is_lost"]:
            safe_send(client_socket, "CLAIM_FAIL::CANNOT_CLAIM_LOST")
            return

        finder = report["owner_id"]
        if finder == claimer:
            safe_send(client_socket, "CLAIM_FAIL::OWN_REPORT")
            return

        # Check for existing active claim by this user
        existing = supabase.table("claims").select("id, status").eq("report_id", report_id).eq("claimer_username", claimer).execute()
        for claim in existing.data:
            if claim["status"] in ["SUBMITTED", "QUESTIONING"]:
                safe_send(client_socket, "CLAIM_FAIL::ALREADY_CLAIMED")
                return

        # Insert claim
        ins = supabase.table("claims").insert({
            "report_id": report_id,
            "claimer_username": claimer,
            "proof_description": proof_description,
            "status": "SUBMITTED",
            "created_at": timestamp
        }).execute()

        if ins.data:
            claim_id = ins.data[0]["id"]

            safe_send(client_socket, f"CLAIM_SUBMITTED::{claim_id}")

            # Notify finder
            create_notification(
                finder,
                "CLAIM",
                f"New claim on '{report['name']}'",
                f"{claimer} claims this item. Proof: {proof_description[:50]}...",
                str(claim_id)
            )

            # Send real-time claim notification to finder if online
            claim_msg = f"CLAIM::NEW::{claim_id}::{report_id}::{claimer}::{proof_description}::{timestamp}"
            send_to_user(finder, claim_msg)

            print(f"[CLAIM] {claimer} submitted claim for report {report_id}")
        else:
            safe_send(client_socket, "CLAIM_FAIL::INSERT")

    except Exception as e:
        print(f"[ERROR] Failed to submit claim: {e}")
        safe_send(client_socket, "CLAIM_FAIL::ERROR")


def handle_get_claims(client_socket, message: str):
    """Get claims for a report (for finder) or by a user (for claimer)"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIMS_DONE")
        return

    get_type = parts[2]  # "REPORT" or "USER"
    identifier = parts[3]  # report_id or username

    try:
        if get_type == "REPORT":
            # Get all claims for a specific report (finder view)
            resp = supabase.table("claims").select("*").eq("report_id", identifier).order("created_at", desc=True).execute()
        elif get_type == "USER":
            # Get all claims by a specific user (claimer view)
            resp = supabase.table("claims").select("*").eq("claimer_username", identifier).order("created_at", desc=True).execute()
        else:
            safe_send(client_socket, "CLAIMS_DONE")
            return

        for c in resp.data:
            # Get report name
            report_resp = supabase.table("reports").select("name, owner_id").eq("id", c["report_id"]).execute()
            report_name = report_resp.data[0]["name"] if report_resp.data else "Unknown"
            report_owner = report_resp.data[0]["owner_id"] if report_resp.data else ""

            msg = (
                f"CLAIM::{c['id']}::{c['report_id']}::{c['claimer_username']}::"
                f"{c['proof_description']}::{c['status']}::{c['created_at']}::{report_name}::{report_owner}"
            )
            safe_send(client_socket, msg)

        safe_send(client_socket, "CLAIMS_DONE")
        print(f"[SERVER] Sent {len(resp.data)} claims for {get_type}:{identifier}")

    except Exception as e:
        print(f"[ERROR] Failed to get claims: {e}")
        safe_send(client_socket, "CLAIMS_DONE")


def handle_claim_question(client_socket, message: str):
    """Finder asks verification question to claimer"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIM_QUESTION_FAIL::FORMAT")
        return

    claim_id = parts[2]
    finder = parts[3]
    question_text = parts[4] if len(parts) > 4 else ""

    if not question_text.strip():
        safe_send(client_socket, "CLAIM_QUESTION_FAIL::EMPTY")
        return

    timestamp = get_timestamp()

    try:
        # Get claim details
        claim_resp = supabase.table("claims").select("claimer_username, report_id, status").eq("id", claim_id).execute()
        if not claim_resp.data:
            safe_send(client_socket, "CLAIM_QUESTION_FAIL::NOT_FOUND")
            return

        claim = claim_resp.data[0]
        claimer = claim["claimer_username"]

        # Update claim status to QUESTIONING
        supabase.table("claims").update({"status": "QUESTIONING"}).eq("id", claim_id).execute()

        # Save question to claim_qa table
        supabase.table("claim_qa").insert({
            "claim_id": claim_id,
            "question": question_text,
            "asked_by": finder,
            "created_at": timestamp
        }).execute()

        safe_send(client_socket, f"CLAIM_QUESTION_SENT::{claim_id}")

        # Notify claimer
        create_notification(
            claimer,
            "CLAIM",
            "Verification question received",
            f"Question: {question_text[:50]}...",
            str(claim_id)
        )

        # Send real-time to claimer
        question_msg = f"CLAIM::QUESTION::{claim_id}::{question_text}::{timestamp}"
        send_to_user(claimer, question_msg)

        print(f"[CLAIM] Question asked for claim {claim_id}")

    except Exception as e:
        print(f"[ERROR] Failed to send claim question: {e}")
        safe_send(client_socket, "CLAIM_QUESTION_FAIL::ERROR")


def handle_claim_answer(client_socket, message: str):
    """Claimer answers verification question"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIM_ANSWER_FAIL::FORMAT")
        return

    claim_id = parts[2]
    claimer = parts[3]
    answer_text = parts[4] if len(parts) > 4 else ""

    if not answer_text.strip():
        safe_send(client_socket, "CLAIM_ANSWER_FAIL::EMPTY")
        return

    timestamp = get_timestamp()

    try:
        # Get claim and report details
        claim_resp = supabase.table("claims").select("report_id").eq("id", claim_id).execute()
        if not claim_resp.data:
            safe_send(client_socket, "CLAIM_ANSWER_FAIL::NOT_FOUND")
            return

        report_id = claim_resp.data[0]["report_id"]

        # Get report owner (finder)
        report_resp = supabase.table("reports").select("owner_id").eq("id", report_id).execute()
        finder = report_resp.data[0]["owner_id"] if report_resp.data else None

        # Update the latest unanswered question with the answer
        qa_resp = supabase.table("claim_qa").select("id").eq("claim_id", claim_id).is_("answer", "null").order("created_at", desc=True).limit(1).execute()

        if qa_resp.data:
            supabase.table("claim_qa").update({
                "answer": answer_text,
                "answered_at": timestamp
            }).eq("id", qa_resp.data[0]["id"]).execute()

        safe_send(client_socket, f"CLAIM_ANSWER_SENT::{claim_id}")

        # Notify finder
        if finder:
            create_notification(
                finder,
                "CLAIM",
                "Verification answer received",
                f"Answer: {answer_text[:50]}...",
                str(claim_id)
            )

            # Send real-time to finder
            answer_msg = f"CLAIM::ANSWER::{claim_id}::{answer_text}::{timestamp}"
            send_to_user(finder, answer_msg)

        print(f"[CLAIM] Answer submitted for claim {claim_id}")

    except Exception as e:
        print(f"[ERROR] Failed to send claim answer: {e}")
        safe_send(client_socket, "CLAIM_ANSWER_FAIL::ERROR")


def handle_get_claim_qa(client_socket, message: str):
    """Get Q&A history for a claim"""
    parts = message.split("::")
    if len(parts) < 3:
        safe_send(client_socket, "CLAIM_QA_DONE")
        return

    claim_id = parts[2]

    try:
        resp = supabase.table("claim_qa").select("*").eq("claim_id", claim_id).order("created_at", desc=False).execute()

        for qa in resp.data:
            answer = qa.get("answer") or ""
            answered_at = qa.get("answered_at") or ""
            msg = f"CLAIM_QA::{qa['id']}::{claim_id}::{qa['question']}::{answer}::{qa['asked_by']}::{qa['created_at']}::{answered_at}"
            safe_send(client_socket, msg)

        safe_send(client_socket, f"CLAIM_QA_DONE::{claim_id}")

    except Exception as e:
        print(f"[ERROR] Failed to get claim Q&A: {e}")
        safe_send(client_socket, "CLAIM_QA_DONE")


def handle_claim_approve(client_socket, message: str):
    """Finder approves a claim"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIM_APPROVE_FAIL::FORMAT")
        return

    claim_id = parts[2]
    finder = parts[3]

    try:
        # Get claim details
        claim_resp = supabase.table("claims").select("claimer_username, report_id").eq("id", claim_id).execute()
        if not claim_resp.data:
            safe_send(client_socket, "CLAIM_APPROVE_FAIL::NOT_FOUND")
            return

        claim = claim_resp.data[0]
        claimer = claim["claimer_username"]
        report_id = claim["report_id"]

        # Verify finder owns the report
        report_resp = supabase.table("reports").select("owner_id, name").eq("id", report_id).execute()
        if not report_resp.data or report_resp.data[0]["owner_id"] != finder:
            safe_send(client_socket, "CLAIM_APPROVE_FAIL::NOT_OWNER")
            return

        report_name = report_resp.data[0]["name"]

        # Update claim status
        supabase.table("claims").update({"status": "APPROVED"}).eq("id", claim_id).execute()

        # Optionally mark report as resolved
        supabase.table("reports").update({"status": "RESOLVED"}).eq("id", report_id).execute()

        # Reject other pending claims for this report
        supabase.table("claims").update({"status": "REJECTED"}).eq("report_id", report_id).neq("id", claim_id).in_("status", ["SUBMITTED", "QUESTIONING"]).execute()

        safe_send(client_socket, f"CLAIM_APPROVED::{claim_id}")

        # Notify claimer
        create_notification(
            claimer,
            "CLAIM",
            "Your claim was approved!",
            f"Your claim for '{report_name}' was approved. Start a chat to arrange pickup!",
            str(claim_id)
        )

        # Send real-time to both
        approve_msg = f"CLAIM::APPROVED::{claim_id}::{report_id}"
        send_to_user(claimer, approve_msg)
        send_to_user(finder, approve_msg)

        # Broadcast report resolved
        broadcast(f"REPORT_RESOLVED::{report_id}")

        print(f"[CLAIM] Claim {claim_id} approved by {finder}")

    except Exception as e:
        print(f"[ERROR] Failed to approve claim: {e}")
        safe_send(client_socket, "CLAIM_APPROVE_FAIL::ERROR")


def handle_claim_reject(client_socket, message: str):
    """Finder rejects a claim"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIM_REJECT_FAIL::FORMAT")
        return

    claim_id = parts[2]
    finder = parts[3]
    reason = parts[4] if len(parts) > 4 else "No reason provided"

    try:
        # Get claim details
        claim_resp = supabase.table("claims").select("claimer_username, report_id").eq("id", claim_id).execute()
        if not claim_resp.data:
            safe_send(client_socket, "CLAIM_REJECT_FAIL::NOT_FOUND")
            return

        claim = claim_resp.data[0]
        claimer = claim["claimer_username"]
        report_id = claim["report_id"]

        # Verify finder owns the report
        report_resp = supabase.table("reports").select("owner_id, name").eq("id", report_id).execute()
        if not report_resp.data or report_resp.data[0]["owner_id"] != finder:
            safe_send(client_socket, "CLAIM_REJECT_FAIL::NOT_OWNER")
            return

        report_name = report_resp.data[0]["name"]

        # Update claim status
        supabase.table("claims").update({
            "status": "REJECTED",
            "rejection_reason": reason
        }).eq("id", claim_id).execute()

        safe_send(client_socket, f"CLAIM_REJECTED::{claim_id}")

        # Notify claimer
        create_notification(
            claimer,
            "CLAIM",
            "Your claim was rejected",
            f"Your claim for '{report_name}' was rejected. Reason: {reason[:50]}...",
            str(claim_id)
        )

        # Send real-time to claimer
        reject_msg = f"CLAIM::REJECTED::{claim_id}::{reason}"
        send_to_user(claimer, reject_msg)

        print(f"[CLAIM] Claim {claim_id} rejected by {finder}")

    except Exception as e:
        print(f"[ERROR] Failed to reject claim: {e}")
        safe_send(client_socket, "CLAIM_REJECT_FAIL::ERROR")


def handle_claim_cancel(client_socket, message: str):
    """Claimer cancels their own claim"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "CLAIM_CANCEL_FAIL::FORMAT")
        return

    claim_id = parts[2]
    claimer = parts[3]

    try:
        # Verify ownership
        claim_resp = supabase.table("claims").select("claimer_username, status").eq("id", claim_id).execute()
        if not claim_resp.data:
            safe_send(client_socket, "CLAIM_CANCEL_FAIL::NOT_FOUND")
            return

        claim = claim_resp.data[0]
        if claim["claimer_username"] != claimer:
            safe_send(client_socket, "CLAIM_CANCEL_FAIL::NOT_OWNER")
            return

        if claim["status"] not in ["SUBMITTED", "QUESTIONING"]:
            safe_send(client_socket, "CLAIM_CANCEL_FAIL::ALREADY_PROCESSED")
            return

        # Update status to CANCELLED
        supabase.table("claims").update({"status": "CANCELLED"}).eq("id", claim_id).execute()

        safe_send(client_socket, f"CLAIM_CANCELLED::{claim_id}")
        print(f"[CLAIM] Claim {claim_id} cancelled by {claimer}")

    except Exception as e:
        print(f"[ERROR] Failed to cancel claim: {e}")
        safe_send(client_socket, "CLAIM_CANCEL_FAIL::ERROR")


# ------------------ LOCATION SHARING (Sprint 5) ------------------

def handle_location_start(client_socket, message: str):
    """Start a location sharing session"""
    parts = message.split("::")
    if len(parts) < 5:
        safe_send(client_socket, "LOCATION::FAIL::FORMAT")
        return

    session_id = parts[2]
    from_user = parts[3]
    to_user = parts[4]

    # Create session
    with location_sessions_lock:
        location_sessions[session_id] = {
            "from_user": from_user,
            "to_user": to_user,
            "status": "PENDING",
            "start_time": datetime.now()
        }

    print(f"[LOCATION] Session {session_id}: {from_user} -> {to_user}")

    # Send invitation to target user
    invite_msg = f"LOCATION::SESSION_INVITE::{session_id}::{from_user}"
    if send_to_user(to_user, invite_msg):
        safe_send(client_socket, f"LOCATION::INVITE_SENT::{session_id}")
    else:
        # User is offline
        safe_send(client_socket, f"LOCATION::FAIL::USER_OFFLINE")
        with location_sessions_lock:
            if session_id in location_sessions:
                del location_sessions[session_id]


def handle_location_accept(client_socket, message: str):
    """Accept a location sharing session"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "LOCATION::FAIL::FORMAT")
        return

    session_id = parts[2]
    username = parts[3]

    with location_sessions_lock:
        if session_id not in location_sessions:
            safe_send(client_socket, "LOCATION::FAIL::SESSION_NOT_FOUND")
            return

        session = location_sessions[session_id]
        if session["to_user"] != username:
            safe_send(client_socket, "LOCATION::FAIL::NOT_INVITED")
            return

        session["status"] = "ACTIVE"
        session["start_time"] = datetime.now()

    # Notify both users
    started_msg = f"LOCATION::SESSION_STARTED::{session_id}"
    send_to_user(session["from_user"], started_msg)
    safe_send(client_socket, started_msg)

    print(f"[LOCATION] Session {session_id} started")

    # Start timeout timer (30 minutes)
    threading.Thread(target=location_session_timeout, args=(session_id,), daemon=True).start()


def handle_location_decline(client_socket, message: str):
    """Decline a location sharing session"""
    parts = message.split("::")
    if len(parts) < 4:
        safe_send(client_socket, "LOCATION::FAIL::FORMAT")
        return

    session_id = parts[2]
    username = parts[3]

    with location_sessions_lock:
        if session_id not in location_sessions:
            return

        session = location_sessions[session_id]
        if session["to_user"] != username:
            return

        del location_sessions[session_id]

    # Notify initiator
    declined_msg = f"LOCATION::SESSION_DECLINED::{session_id}"
    send_to_user(session["from_user"], declined_msg)

    print(f"[LOCATION] Session {session_id} declined")


def handle_location_update(client_socket, message: str):
    """Handle location update from a user"""
    parts = message.split("::")
    if len(parts) < 7:
        return

    session_id = parts[2]
    username = parts[3]
    lat = parts[4]
    lng = parts[5]
    accuracy = parts[6]

    with location_sessions_lock:
        if session_id not in location_sessions:
            return

        session = location_sessions[session_id]
        if session["status"] != "ACTIVE":
            return

        # Determine the other user
        if username == session["from_user"]:
            other_user = session["to_user"]
        elif username == session["to_user"]:
            other_user = session["from_user"]
        else:
            return

    # Relay location to other user
    update_msg = f"LOCATION::UPDATE::{session_id}::{username}::{lat}::{lng}::{accuracy}"
    send_to_user(other_user, update_msg)


def handle_location_end(client_socket, message: str):
    """End a location sharing session"""
    parts = message.split("::")
    if len(parts) < 4:
        return

    session_id = parts[2]
    username = parts[3]

    with location_sessions_lock:
        if session_id not in location_sessions:
            return

        session = location_sessions[session_id]
        from_user = session["from_user"]
        to_user = session["to_user"]

        del location_sessions[session_id]

    # Notify both users
    ended_msg = f"LOCATION::SESSION_ENDED::{session_id}"
    send_to_user(from_user, ended_msg)
    send_to_user(to_user, ended_msg)

    print(f"[LOCATION] Session {session_id} ended by {username}")


def location_session_timeout(session_id: str):
    """Auto-end session after 30 minutes"""
    import time
    time.sleep(30 * 60)  # 30 minutes

    with location_sessions_lock:
        if session_id not in location_sessions:
            return  # Already ended

        session = location_sessions[session_id]
        from_user = session["from_user"]
        to_user = session["to_user"]

        del location_sessions[session_id]

    # Notify both users
    timeout_msg = f"LOCATION::SESSION_ENDED::{session_id}::TIMEOUT"
    send_to_user(from_user, timeout_msg)
    send_to_user(to_user, timeout_msg)

    print(f"[LOCATION] Session {session_id} timed out")


# ------------------ CLIENT LOOP ------------------

def handle_client(client_socket):
    current_user = None  # Track logged-in user for this connection

    with clients_lock:
        clients.append(client_socket)

    buffer = ""
    while True:
        try:
            data = client_socket.recv(4096)
            if not data:
                break

            buffer += data.decode("utf-8")

            # Process complete messages (split by newline)
            while "\n" in buffer:
                message, buffer = buffer.split("\n", 1)
                message = message.strip()
                if not message:
                    continue

                print("[RECEIVED]", message)

                # Authentication
                if message.startswith("AUTH::"):
                    result = handle_auth(client_socket, message)
                    if result:
                        current_user = result

                # Reports
                elif message == "REPORTS::GET":
                    send_all_reports(client_socket)

                elif message.startswith("REPORTS::GET_MINE::"):
                    parts = message.split("::")
                    if len(parts) >= 3:
                        send_user_reports(client_socket, parts[2])

                elif message.startswith("REPORT::RESOLVE::"):
                    handle_report_resolve(client_socket, message)

                elif message.startswith("REPORT::DELETE::"):
                    handle_report_delete(client_socket, message)

                elif message.startswith("REPORT::UPDATE::"):
                    handle_report_update(client_socket, message)

                elif message.startswith("REPORT::"):
                    handle_report_create(client_socket, message)

                elif message.startswith("SEARCH::"):
                    handle_search(client_socket, message)

                # Notifications
                elif message.startswith("NOTIFICATIONS::GET::"):
                    handle_get_notifications(client_socket, message)

                elif message.startswith("NOTIFICATION::READ::"):
                    handle_mark_notification_read(client_socket, message)

                elif message.startswith("NOTIFICATIONS::READ_ALL::"):
                    handle_mark_all_notifications_read(client_socket, message)

                elif message.startswith("NOTIFICATIONS::UNREAD_COUNT::"):
                    handle_get_unread_count(client_socket, message)

                # Chat
                elif message.startswith("CHAT::SEND::"):
                    handle_chat_send(client_socket, message)

                elif message.startswith("CHAT::HISTORY::"):
                    handle_chat_history(client_socket, message)

                elif message.startswith("CHAT::TYPING::"):
                    handle_chat_typing(client_socket, message)

                elif message.startswith("CHAT::READ::"):
                    handle_chat_read(client_socket, message)

                elif message.startswith("CHAT::CONVERSATIONS::"):
                    handle_get_conversations(client_socket, message)

                # Presence
                elif message.startswith("PRESENCE::CHECK::"):
                    handle_presence_check(client_socket, message)

                elif message.startswith("HEARTBEAT::"):
                    handle_heartbeat(client_socket, message, current_user)

                # Matches
                elif message.startswith("MATCHES::GET::"):
                    handle_get_matches(client_socket, message)

                elif message.startswith("MATCH::CONFIRM::"):
                    handle_match_confirm(client_socket, message)

                elif message.startswith("MATCH::DISMISS::"):
                    handle_match_dismiss(client_socket, message)

                # Subscriptions
                elif message.startswith("SUBSCRIBE::REPORT::"):
                    handle_subscribe(client_socket, message)

                elif message.startswith("UNSUBSCRIBE::REPORT::"):
                    handle_unsubscribe(client_socket, message)

                # Comments
                elif message.startswith("COMMENT::GET::"):
                    handle_get_comments(client_socket, message)

                elif message.startswith("COMMENT::POST::"):
                    handle_post_comment(client_socket, message)

                elif message.startswith("COMMENT::DELETE::"):
                    handle_delete_comment(client_socket, message)

                # Claims
                elif message.startswith("CLAIM::SUBMIT::"):
                    handle_claim_submit(client_socket, message)

                elif message.startswith("CLAIMS::GET::"):
                    handle_get_claims(client_socket, message)

                elif message.startswith("CLAIM::QUESTION::"):
                    handle_claim_question(client_socket, message)

                elif message.startswith("CLAIM::ANSWER::"):
                    handle_claim_answer(client_socket, message)

                elif message.startswith("CLAIM::QA::"):
                    handle_get_claim_qa(client_socket, message)

                elif message.startswith("CLAIM::APPROVE::"):
                    handle_claim_approve(client_socket, message)

                elif message.startswith("CLAIM::REJECT::"):
                    handle_claim_reject(client_socket, message)

                elif message.startswith("CLAIM::CANCEL::"):
                    handle_claim_cancel(client_socket, message)

                # Location Sharing (Sprint 5)
                elif message.startswith("LOCATION::START::"):
                    handle_location_start(client_socket, message)

                elif message.startswith("LOCATION::ACCEPT::"):
                    handle_location_accept(client_socket, message)

                elif message.startswith("LOCATION::DECLINE::"):
                    handle_location_decline(client_socket, message)

                elif message.startswith("LOCATION::UPDATE::"):
                    handle_location_update(client_socket, message)

                elif message.startswith("LOCATION::END::"):
                    handle_location_end(client_socket, message)

                # Register socket for user (used by location sharing)
                elif message.startswith("REGISTER_SOCKET::"):
                    parts = message.split("::")
                    if len(parts) >= 2:
                        username = parts[1]
                        register_user_socket(username, client_socket)
                        if not current_user:
                            current_user = username

        except Exception as e:
            print("[ERROR]", e)
            break

    # Cleanup on disconnect
    with clients_lock:
        if client_socket in clients:
            clients.remove(client_socket)

    if current_user:
        unregister_user_socket(current_user)

    client_socket.close()


# ------------------ START ------------------

def start_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen()
    print(f"[SERVER STARTED] {HOST}:{PORT}")

    while True:
        sock, addr = server.accept()
        print("[NEW CONNECTION]", addr)
        threading.Thread(target=handle_client, args=(sock,), daemon=True).start()


if __name__ == "__main__":
    start_server()

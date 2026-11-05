"""Writes synthetic Reddit-shaped JSON for Resync's debug demo mode and unit tests."""
import json, sys, time
from pathlib import Path

out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
now = 1762300000  # fixed so tests are stable

def pic(seed, w=1080, h=720):
    return f"https://picsum.photos/seed/{seed}/{w}/{h}"

def preview(seed, w, h):
    return {"images": [{
        "source": {"url": pic(seed, w, h), "width": w, "height": h},
        "resolutions": [{"url": pic(seed, 640, int(640*h/w)), "width": 640, "height": int(640*h/w)},
                        {"url": pic(seed, 1080, int(1080*h/w)), "width": 1080, "height": int(1080*h/w)}],
        "variants": {}, "id": seed}], "enabled": True}

def post(id, sub, title, author, score, comments, age_h, **extra):
    d = {"id": id, "name": f"t3_{id}", "subreddit": sub, "author": author, "title": title,
         "selftext": "", "selftext_html": None, "url": f"https://www.reddit.com/r/{sub}/comments/{id}/",
         "domain": f"self.{sub}", "permalink": f"/r/{sub}/comments/{id}/demo/", "score": score,
         "num_comments": comments, "created_utc": float(now - age_h*3600), "over_18": False,
         "spoiler": False, "stickied": False, "is_self": False, "likes": None, "saved": False,
         "link_flair_text": None}
    d.update(extra)
    return {"kind": "t3", "data": d}

posts = [
    post("dm0001", "resync", "Welcome to the Resync demo feed", "resync_bot", 42, 3, 1, stickied=True, is_self=True,
         selftext="This feed is bundled sample data so the UI can be checked without an API key.",
         selftext_html="<div class=\"md\"><p>This feed is bundled sample data so the UI can be checked without an API key. "
                       "Links like <a href=\"/r/resync\">r/resync</a> open in the app.</p></div>",
         link_flair_text="Announcement"),
    post("dm0002", "pics", "Morning fog rolling over the valley", "lens_cap", 18234, 412, 5,
         url="https://i.redd.it/demo-fog.jpg", domain="i.redd.it", post_hint="image", preview=preview("fog", 1600, 1067)),
    post("dm0003", "Android", "Three screenshots of my home screen setup", "widget_fan", 1290, 233, 9,
         url="https://www.reddit.com/gallery/dm0003", domain="reddit.com", is_gallery=True,
         gallery_data={"items": [{"media_id": "g1", "id": 1}, {"media_id": "g2", "id": 2}, {"media_id": "g3", "id": 3}]},
         media_metadata={k: {"status": "valid", "e": "Image", "m": "image/jpg",
                             "s": {"u": pic(k, 1080, 2340), "x": 1080, "y": 2340},
                             "p": [{"u": pic(k, 640, 1387), "x": 640, "y": 1387}, {"u": pic(k, 1080, 2340), "x": 1080, "y": 2340}]}
                         for k in ("g1", "g2", "g3")}),
    post("dm0004", "technology", "Open-source app keeps an old phone useful for another five years", "patch_notes", 5021, 640, 14,
         url="https://example.com/articles/old-phone", domain="example.com", post_hint="link", preview=preview("phone", 1200, 630)),
    post("dm0005", "aww", "He figured out the door handle", "door_watch", 30411, 801, 20,
         url="https://v.redd.it/demo05", domain="v.redd.it", is_video=True, post_hint="hosted:video", preview=preview("dog", 1080, 1350),
         secure_media={"reddit_video": {"hls_url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                        "fallback_url": "https://v.redd.it/demo05/DASH_720.mp4", "width": 1080, "height": 1350}}),
    post("dm0006", "movies", "Ending explained (spoilers obviously)", "plot_holes", 870, 1022, 26, spoiler=True,
         url="https://i.redd.it/demo-ending.png", domain="i.redd.it", post_hint="image", preview=preview("cinema", 1920, 1080),
         link_flair_text="Discussion"),
    post("dm0008", "gifs", "The planet, spinning", "orbit_cam", 2400, 88, 33,
         url="https://upload.wikimedia.org/wikipedia/commons/2/2c/Rotating_earth_%28large%29.gif", domain="upload.wikimedia.org",
         post_hint="image", preview=preview("earth", 400, 400)),
    post("dm0007", "AskReddit", "What small habit made the biggest difference in your week?", "curious_q", 9120, 4302, 30, is_self=True,
         selftext="", selftext_html=None),
]
listing = {"kind": "Listing", "data": {"after": "t3_dm0007", "dist": len(posts), "children": posts}}
(out / "listing.json").write_text(json.dumps(listing, indent=1))

def c(id, parent, depth, author, body, score, age_h, replies=None, **extra):
    d = {"id": id, "name": f"t1_{id}", "parent_id": parent, "depth": depth, "author": author, "body": body,
         "body_html": f"<div class=\"md\"><p>{body}</p></div>", "score": score, "score_hidden": False,
         "created_utc": float(now - age_h*3600), "edited": False, "is_submitter": False, "distinguished": None,
         "stickied": False, "likes": None, "author_flair_text": None,
         "replies": {"kind": "Listing", "data": {"children": replies}} if replies else ""}
    d.update(extra)
    return {"kind": "t1", "data": d}

def more(id, parent, depth, children):
    return {"kind": "more", "data": {"id": id, "name": f"t1_{id}", "parent_id": parent, "depth": depth,
                                     "count": len(children), "children": children}}

thread_post = post("dm0007", "AskReddit", "What small habit made the biggest difference in your week?", "curious_q", 9120, 4302, 30,
                   is_self=True, selftext="Curious what actually stuck for people.",
                   selftext_html="<div class=\"md\"><p>Curious what actually stuck for people.</p></div>")
tree = [
    c("ca1", "t3_dm0007", 0, "AutoModerator", "Reminder: keep it civil.", 1, 30, distinguished="moderator", stickied=True),
    c("cb1", "t3_dm0007", 0, "early_bird", "Putting my phone in another room at night. Sleep went way up.", 5120, 29, replies=[
        c("cb2", "t1_cb1", 1, "night_owl", "Tried this, lasted two days. Respect.", 980, 28, replies=[
            c("cb3", "t1_cb2", 2, "early_bird", "The trick is a cheap alarm clock so there's no excuse.", 760, 27, is_submitter=False, replies=[
                c("cb4", "t1_cb3", 3, "curious_q", "Ordering one right now.", 301, 26, is_submitter=True),
            ]),
        ]),
        more("cb9", "t1_cb1", 1, ["cb5", "cb6"]),
    ]),
    c("cc1", "t3_dm0007", 0, "list_maker", "Writing tomorrow's three tasks before bed. See <a href=\"/r/productivity\">r/productivity</a>.", 2210, 25,
      edited=float(now - 20*3600), author_flair_text="Organized-ish"),
    c("cd1", "t3_dm0007", 0, "plant_person", "Watering plants first thing. Five quiet minutes before the day starts. >!Also coffee.!<", 640, 22, likes=True,
      body_html='<div class="md"><p>Watering plants first thing. Five quiet minutes before the day starts. <span class="md-spoiler-text">Also coffee.</span></p></div>'),
    more("ce1", "t3_dm0007", 0, ["ce1", "cf1", "cg1"]),
]
comments = [
    {"kind": "Listing", "data": {"after": None, "children": [thread_post]}},
    {"kind": "Listing", "data": {"after": None, "children": tree}},
]
(out / "comments.json").write_text(json.dumps(comments, indent=1))

# /api/morechildren: flat, with a reply listed before its parent to exercise reordering.
things = [
    c("cb6", "t1_cb5", 2, "late_reply", "Same, and I stopped doom-scrolling in bed.", 44, 10),
    c("cb5", "t1_cb1", 1, "second_try", "Week three of this. It works.", 120, 12),
]
(out / "morechildren.json").write_text(json.dumps({"json": {"errors": [], "data": {"things": things}}}, indent=1))
(out / "me.json").write_text(json.dumps({"name": "demo_user", "inbox_count": 2}))
subs = [{"kind": "t5", "data": {"display_name": n}} for n in ("Android", "aww", "AskReddit", "movies", "pics", "technology")]
(out / "subreddits.json").write_text(json.dumps({"kind": "Listing", "data": {"after": None, "children": subs}}, indent=1))
print("wrote", sorted(p.name for p in out.iterdir()))

# --- Milestone 2 endpoints ---
def t5(name, title, subs, desc, sub=False, nsfw=False):
    return {"kind": "t5", "data": {"display_name": name, "name": f"t5_{name.lower()}", "id": name.lower(), "title": title,
            "public_description": desc, "description_html": f"<div class=\"md\"><p>{desc}</p><h2>Rules</h2><ol><li><p>Be kind.</p></li><li><p>Stay on topic.</p></li></ol></div>",
            "subscribers": subs, "active_user_count": subs // 400, "community_icon": pic(f"icon-{name}", 256, 256),
            "banner_background_image": pic(f"banner-{name}", 1600, 400), "user_is_subscriber": sub, "over18": nsfw,
            "created_utc": float(now - 9 * 365 * 86400)}}

(out / "subreddit_about.json").write_text(json.dumps(t5("Android", "Android news and discussion", 4_100_000,
    "A place for Android news, discussion and questions.", sub=True), indent=1))
(out / "subreddit_search.json").write_text(json.dumps({"kind": "Listing", "data": {"after": None, "children": [
    t5("Android", "Android news and discussion", 4_100_000, "A place for Android news, discussion and questions.", sub=True),
    t5("androiddev", "Android developers", 260_000, "News and discussion for Android developers."),
    t5("AndroidApps", "Android apps", 410_000, "Find and share great Android apps."),
]}}, indent=1))

(out / "user_about.json").write_text(json.dumps({"kind": "t2", "data": {"name": "early_bird", "link_karma": 12840,
    "comment_karma": 48211, "created_utc": float(now - 6 * 365 * 86400), "icon_img": pic("avatar", 256, 256),
    "subreddit": {"public_description": "Mornings, coffee, and too many houseplants."}}}, indent=1))

def uc(id, sub, post_id, post_title, body, score, age_h):
    return {"kind": "t1", "data": {"id": id, "name": f"t1_{id}", "author": "early_bird", "body": body,
            "body_html": f"<div class=\"md\"><p>{body}</p></div>", "score": score, "created_utc": float(now - age_h*3600),
            "subreddit": sub, "link_id": f"t3_{post_id}", "link_title": post_title, "likes": None, "saved": False,
            "parent_id": f"t3_{post_id}", "depth": 0}}

(out / "user_overview.json").write_text(json.dumps({"kind": "Listing", "data": {"after": None, "children": [
    uc("ub1", "AskReddit", "dm0007", "What small habit made the biggest difference in your week?",
       "Putting my phone in another room at night. Sleep went way up.", 5120, 29),
    posts[1],
    uc("ub2", "pics", "dm0002", "Morning fog rolling over the valley", "Where was this taken? The light is unreal.", 88, 40),
]}}, indent=1))

def msg(kind, id, author, subject, body, age_h, new, **extra):
    d = {"id": id, "name": f"{kind}_{id}", "author": author, "dest": "demo_user", "subject": subject, "body": body,
         "body_html": f"<div class=\"md\"><p>{body}</p></div>", "created_utc": float(now - age_h*3600), "new": new,
         "was_comment": kind == "t1"}
    d.update(extra)
    return {"kind": kind, "data": d}

(out / "inbox.json").write_text(json.dumps({"kind": "Listing", "data": {"after": None, "children": [
    msg("t1", "cb4", "curious_q", "comment reply", "Ordering one right now.", 26, True,
        link_title="What small habit made the biggest difference in your week?", subreddit="AskReddit",
        context="/r/AskReddit/comments/dm0007/what_small_habit/cb4/?context=3"),
    msg("t4", "m1", "plant_person", "Cutting swap?", "Saw your comment about houseplants. Want to trade pothos cuttings?", 50, True),
    msg("t1", "cc9", "list_maker", "post reply", "Great question, saving this thread.", 70, False,
        link_title="What small habit made the biggest difference in your week?", subreddit="AskReddit",
        context="/r/AskReddit/comments/dm0007/what_small_habit/cc9/?context=3"),
]}}, indent=1))

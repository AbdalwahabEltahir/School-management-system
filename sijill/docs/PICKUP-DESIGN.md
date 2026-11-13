# Hafez: student pick-up and collection, design for approval

*Status: **built** with the recommended answers to the six questions at the end (they were not answered). Each answer is one small change if you want it different.*

## What it does

A parent keeps a short list of people allowed to collect each child. At pick-up time the parent presses **I've arrived**. The class teacher and the coordinator are alerted at once. A member of staff checks the collector's identity against the list, presses **Release**, and Hafez records who collected which child and when. A person who is not on the list can never be released through Hafez.

## Who does what

| Person | Can |
|---|---|
| **Parent** | Add, edit and remove collectors for their own children. Press **I've arrived** for a child, choosing who is collecting. Cancel a request. See the status and the release record of their own children only. |
| **Teacher** of the child's class | See arrivals for their own classes. Verify and release. |
| **Coordinator** | See arrivals for the whole school. Verify and release. |
| **Principal, School IT** | Read the live board and the log (read only). |
| **Student, other roles** | Nothing. |

The collector is only a record (name, relationship, ID number, phone). They never sign in.

## The flow

```
Parent                      Hafez                           Teacher / Coordinator
  | "I've arrived" (child,    |                                    |
  |  collector)               |--- alert: "Amal's father has ----->|  bell notification
  |                           |    arrived" (pick-up kind)         |
  |                           |                                    |  Live board:
  |                           |   ARRIVED -> shown at the top      |  Arrived | Waiting | Released today
  |                           |                                    |
  |                           |<-- verify ID against the list -----|  "Verify and release"
  |<-- "Amal was released" ---|    RELEASED (log entry written)    |  or "Decline" (with a reason)
```

Statuses: **Arrived** (waiting for staff), **Released**, **Declined**, **Cancelled** (parent changed their mind).

## Screens

- **Parent, "Pick-up":** per child, the approved collectors (add / remove) and a large **I've arrived** button with a "who is collecting" choice (the parent, or one of the approved collectors). Shows the current request and its status.
- **Staff, "Pick-up board":** live list in three groups: *Arrived* (oldest first, highlighted), *Pending* (children expected but not yet arrived, if the school uses expected pick-ups, see question 5) and *Released today*. Each Arrived card shows the child's name and class, the collector's name and relationship and the last four digits of their ID, with **Verify and release** and **Decline**. The board refreshes every few seconds.
- **Staff, "Release log":** the chain-of-custody record, searchable by date and student.

## The chain-of-custody record

Every release writes one permanent row: **student name, collector name, exact date and time, the staff member who released**. It cannot be edited or deleted. The same event also goes into the existing tamper-evident activity log. A decline or a cancellation is recorded the same way.

## Safety rules

- Release works only for a collector who is on the child's approved list at that moment, and only after a staff member presses the button. Hafez never releases anyone automatically.
- A parent can only manage collectors of their own children (the same link used for the portal).
- ID numbers are stored hashed, with the last four digits kept for the staff check. Photos are not stored.
- Everything is limited to the person's own school. All changes are written to the activity log with who and when.

## What is added to the system

Three tables (collectors, pick-up requests, release log), a new alert type "Pick-up" in notification settings (so people can switch it off, like other alerts), a new menu item for parents and staff, and Arabic and English text for every new screen. Nothing existing changes.

## Questions for you

1. **Collector approval.** Should a newly added collector work immediately (my recommendation: staff verify ID at the gate anyway), or must the school office approve each new collector first?
2. **Who releases.** Teacher and coordinator both (as written above), or coordinator only?
3. **ID check.** Is "ID number last four digits shown to staff" enough, or should the parent also upload a photo of the collector?
4. **Hours.** Should **I've arrived** only work during a school-day window (for example 11:00 to 15:00), or at any time?
5. **Expected pick-ups.** Do you want the *Pending* group (the parent pre-announces "my driver will collect today"), or only live arrivals?
6. **Location.** Confirm there is **no** GPS check on **I've arrived** (my recommendation, for privacy and simplicity).

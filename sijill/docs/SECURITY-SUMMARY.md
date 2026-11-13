# Hafez (حافظ): security summary

*One page, for EAA presentations. Plain English.*

**What Hafez protects.** Student marks, attendance, parent contacts and the files each school keeps in Google Drive.

## Who can do what

| Role | Can | Cannot |
|---|---|---|
| **EAA administrator** | Add, rename and remove schools. Change the rules. Disconnect Google Drive. Handle tickets that principals write to Leadership. | Work inside a school's classes. |
| **Leadership** | See network totals, incidents, reports and the activity log. Read and answer the tickets principals write to Leadership. | Change anything else. Leadership is otherwise read-only. |
| **School IT** | Look after their own school's people (including their roles) and answer their staff's support tickets. | Change rules, rename or remove schools, or disconnect Google Drive. |
| **Principal** | See their own school: marks, points, lesson plans, files and tickets. Write tickets to School IT or to Leadership. | Change marks, give points or publish results (view only). |
| **Coordinator, teacher** | Work with their own school's students and files. Teachers enter and publish marks, give recognition points and write lesson plans. | See other schools. Teachers only read attendance. |
| **Attendance officer** | Record daily attendance. | Anything else. |
| **Student, parent** | See only their own record, or their own children's. | See anyone else. |

Each person can see only their own school. A record from another school is treated as if it does not exist.

## One action needs a second person: deleting a file

A teacher (the file's owner) or a coordinator can ask to delete a file from the register. Nothing is deleted until a **different** person (the principal, a coordinator or School IT of that school) approves. The system refuses to let you approve your own request, and every request, approval and rejection is recorded. Hafez only removes the file from its own register; the Google Drive file is never touched. Everything else (publishing marks, changing roles, removing a school, disconnecting Google Drive) happens directly, limited by role, and is written to the activity log.

## The activity log

Every view and change of a student record, and every approval, is written to a log with **who**, **what** and **when**. Each entry is chained to the one before it, so any later alteration is detectable, and anyone senior can press *Verify the log* to prove it. Names are masked in downloaded exports.

## Support tickets and alerts

Staff (teachers, coordinators, attendance officers and principals) can write a support ticket to their own school's School IT (title, description, category, urgency and one optional attachment of up to 2 MB). A principal can also write to Leadership at head office. A ticket is visible only to the person who wrote it, the team it is addressed to (School IT of that school, or Leadership and EAA administrators) and, read only, the school's principal. Attachments are limited to a short list of safe file types and are always downloaded, never opened inside Hafez. Every ticket step is in the activity log.

People are told about what matters to them: weekly grade deadlines, school notices, ticket updates, and student pick-up, and security and account events (a role change, a file-deletion request waiting, a locked account). Each person can switch most alert types off in their own settings. **Security alerts cannot be switched off.**

## Hafez never changes your files

Hafez reads the school's Google Drive with a **read-only** account. It has no way to edit, move or delete a file.

## Parents and students

There is no public sign-up. A school creates each account and issues a one-time invitation code. A parent can then link only the children the school has approved.

## Sign-in

People sign in with their own school Google account; Hafez stores no staff passwords. After five wrong password attempts on a student or parent account, the account locks. *(Multi-factor sign-in is planned for a later phase.)*

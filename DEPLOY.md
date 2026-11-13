# Deploying the Hafez demo on Render

The repository root has a Render Blueprint ([render.yaml](render.yaml)) that creates the app (Docker, from `sijill/Dockerfile`)
and a Postgres database.

1. Push this repository to GitHub.
2. In Render: **New +** → **Blueprint**, pick the repository, and apply. Render builds the image and creates the database.
3. Open the service URL. The first start fills the empty database with a demo school.

The demo setup turns on the **test sign-in**: on the welcome page press **Sign in with a name (testing)**, type a name or pick a
person. There is no password, so anyone who knows the link can sign in as anyone. Use it for the mentor's test only, then set
`SIJILL_SIGNIN_TEST_MODE` to `false` in Render's Environment.

## The sign-in screen: exactly ten accounts

Test sign-in offers only these accounts, matched on the person's whole name and role (the list is in `TestSignInController`; CI fails
if anything else is offered):

| Name | Role |
|---|---|
| Abdalwahab | School IT |
| Clerk | Attendance Clerk |
| EAA | EAA IT (the only role that adds, edits and removes rules and schools) |
| Huda | Principal |
| Nora | Leadership |
| Parent | Parent |
| Rajaa | Coordinator |
| Student | Student |
| Teacher1, Teacher2 | Teachers |

The invented demo people in the five schools are never offered, so they cannot be used to sign in. For these ten accounts to exist on
Render, copy the local database across with `scripts/copy-db-to-render.ps1`.

## Demo schools and accounts

The demo network has exactly five invented schools, each with about four months of attendance, weekly grades, points, notices,
file issues and support tickets. Every school improves over the months, from strong (A, B) to struggling and climbing (D, E).

| School | Domain | How it is doing |
|---|---|---|
| A: Al Noor Academy | alnoor-academy.edu.qa | strong |
| B: Al Rayyan Model School | rayyan-model.edu.qa | good |
| C: Doha Heritage School | doha-heritage.edu.qa | in the middle, improving |
| D: Al Khor Preparatory School | alkhor-prep.edu.qa | struggling, recovering |
| E: Al Wakrah Independent School | wakrah-independent.edu.qa | struggling, climbing fast |

School A (built first, with the network team) has these accounts:

| Name | Role |
|---|---|
| Lina Sabbagh, Maha Al-Thani | EAA administrators (two, so the four-eyes rule can be tried) |
| Noora Al-Kuwari | Leadership (read only) |
| Omar Fathi | School IT |
| Reem Khalil | Principal |
| Huda Saleh | Coordinator (Mathematics) |
| Sara Mansour, Layla Haddad | Teachers (Mathematics, English) |
| Yasmin Al-Attiyah | Attendance officer |
| Amal Al-Sulaiti, Bader Al-Mohannadi, Dana Al-Kuwari | Students (class 7B) |
| Maha Al-Sulaiti | Parent (follows Amal and Bader) |

Schools B to E each have a principal, School IT, an attendance officer, three coordinators, three teachers, twelve students and
three parents. Their names are printed in the application log when the school is created. On a database that already holds
schools, the seed counts them towards the five and only adds the missing ones (a database with two schools gets C, D and E).

## Notes

- Free Render services sleep after about 15 minutes idle (the first page load then takes up to a minute), and the free database
  expires after a limited period. Open the link a few minutes before the mentor does.
- The Google Drive connector and the scheduled jobs are off in the demo (`SIJILL_GOOGLE_DRIVE_ENABLED`, `SIJILL_JOBS_ENABLED`),
  so no Google credentials are needed. To use Google sign-in and Drive, set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, add
  `https://<your-service>.onrender.com/login/oauth2/code/google` as a redirect URI in Google Cloud Console, and switch the
  connector on.
- Sessions live in memory, so a restart signs everyone out.

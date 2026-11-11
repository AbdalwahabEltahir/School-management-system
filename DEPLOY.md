# Deploying the Sijill demo on Render

The repository root has a Render Blueprint ([render.yaml](render.yaml)) that creates the app (Docker, from `sijill/Dockerfile`)
and a Postgres database.

1. Push this repository to GitHub.
2. In Render: **New +** → **Blueprint**, pick the repository, and apply. Render builds the image and creates the database.
3. Open the service URL. The first start fills the empty database with a demo school.

The demo setup turns on the **test sign-in**: on the welcome page press **Sign in with a name (testing)**, type a name or pick a
person. There is no password, so anyone who knows the link can sign in as anyone. Use it for the mentor's test only, then set
`SIJILL_SIGNIN_TEST_MODE` to `false` in Render's Environment.

## Demo accounts (school: "School B")

| Name | Role | Type this |
|---|---|---|
| Lina Sabbagh | Leadership (whole network) | `Lina` |
| Omar Fathi | School IT | `Omar` |
| Reem Khalil | Principal | `Reem` |
| Huda Saleh | Coordinator (Mathematics) | `Huda` |
| Sara Mansour | Teacher (Mathematics) | `Sara` |
| Layla Haddad | Teacher (English) | `Layla` |
| Amal Student, Bader Student, Cara Student | Students (class 7B) | `Amal`, `Bader`, `Cara` |
| Maha Parent | Parent (follows Amal and Bader) | `Maha` |

The demo includes four files with real rule results (public link, personal drive, exam shared early), a class with a timetable,
published marks, a notice and a study material.

## Notes

- Free Render services sleep after about 15 minutes idle (the first page load then takes up to a minute), and the free database
  expires after a limited period. Open the link a few minutes before the mentor does.
- The Google Drive connector and the scheduled jobs are off in the demo (`SIJILL_GOOGLE_DRIVE_ENABLED`, `SIJILL_JOBS_ENABLED`),
  so no Google credentials are needed. To use Google sign-in and Drive, set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, add
  `https://<your-service>.onrender.com/login/oauth2/code/google` as a redirect URI in Google Cloud Console, and switch the
  connector on.
- Sessions live in memory, so a restart signs everyone out.

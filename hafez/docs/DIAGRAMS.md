# Hafez at a glance

Block diagrams written in [Mermaid](https://mermaid.js.org). GitHub, VS Code (Markdown preview with a Mermaid extension) and
https://mermaid.live all draw them from the code below.

## 1. The big picture

Hafez watches the files in each school's Google Drive, checks them against rules, and tells the right person what to fix.
It also runs a small school portal (classes, marks, attendance, notices).

```mermaid
flowchart LR
    %% ---------- who uses it
    subgraph People["Who uses it"]
        direction TB
        L["Leadership<br/>(head office)"]
        P["Principal"]
        IT["School IT"]
        C["Coordinator"]
        T["Teacher"]
        S["Student"]
        PA["Parent"]
    end

    %% ---------- the app
    subgraph App["Hafez web app (Spring Boot, one service)"]
        direction TB
        UI["Web screens<br/>one dashboard per role"]

        subgraph Health["File health"]
            direction TB
            CHK["Checks<br/>nightly + on change"]
            RULES["Rule engine<br/>rules R1, R2, R10 ..."]
            ISS["Issues and incidents<br/>one rule broken by one file"]
            SCORE["Health score<br/>100 - (normal + 3 x urgent) / files"]
            NOTE["Notifications<br/>owner, reminder, follow-up"]
        end

        subgraph Portal["School portal"]
            direction TB
            ACA["Classes, timetable"]
            MRK["Marks and results"]
            ATT["Attendance"]
            NTC["Notices and materials"]
        end

        ORG["People, schools and access<br/>who may see what"]
        AUD["Activity log (audit)"]
    end

    %% ---------- outside
    GD[("Google Workspace<br/>Drive, read-only")]
    DB[("PostgreSQL<br/>database")]
    MAIL["Email / in-app messages"]

    People --> UI
    UI --> ORG
    UI --> Health
    UI --> Portal

    GD -- "files, owners, sharing" --> CHK
    CHK --> RULES --> ISS
    ISS --> SCORE
    ISS --> NOTE --> MAIL
    ISS --> AUD

    Health --> DB
    Portal --> DB
    ORG --> DB
    AUD --> DB
```

## 2. How a problem travels (the heart of the system)

```mermaid
flowchart LR
    A["A file in Google Drive<br/>e.g. mark book shared with 'anyone with the link'"]
    B["Check reads the file<br/>(read-only)"]
    C["Rule engine<br/>R2: no public links on sensitive files"]
    D["Issue opened<br/>severity: urgent"]
    E["Owner is told<br/>and fixes the file"]
    F["Re-check<br/>only a re-check closes an issue"]
    G["Issue closed<br/>score goes up"]
    H["Not fixed in time<br/>reminder, then 'needs follow-up'<br/>coordinator and principal see it"]

    A --> B --> C --> D --> E --> F
    F -- "fixed" --> G
    F -- "still broken" --> H --> E
```

## 3. Who sees what

```mermaid
flowchart TB
    subgraph Network["Whole network"]
        L["Leadership<br/>totals only: scores, counts,<br/>urgent issues by school and rule<br/>never file names or contents"]
    end
    subgraph School["One school"]
        P["Principal<br/>school score, trend, issues, incidents"]
        IT["School IT<br/>files, connection, unlock marks,<br/>resolve incidents"]
        C["Coordinator<br/>issues for their subject, follow-ups"]
        T["Teacher<br/>own file issues,<br/>marks, attendance, materials"]
    end
    subgraph Home["Students and families"]
        S["Student<br/>own timetable, results, attendance"]
        PA["Parent<br/>children's results and attendance"]
    end
    L --> P --> C --> T
    P --> IT
    T -. "teaches" .-> S
    S -. "followed by" .-> PA
```

## 4. Where it runs

```mermaid
flowchart LR
    U["Browser"] --> R["Render web service<br/>(Docker, Java 21)"]
    R --> PG[("Render Postgres")]
    R -- "OAuth sign-in and Drive read" --> G["Google"]
    GH["GitHub (main)"] -- "CI tests, then auto-deploy" --> R
```

### Reading tip for new users
Start with diagram 2: everything else exists to support that loop. Diagram 1 shows the parts, diagram 3 shows who gets
which view, diagram 4 shows where it is hosted.

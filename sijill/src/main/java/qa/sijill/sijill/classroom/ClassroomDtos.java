package qa.sijill.sijill.classroom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of homework, quizzes and the teacher chat. */
public final class ClassroomDtos {

    private ClassroomDtos() {
    }

    // ---- homework
    public record HomeworkRequest(@NotNull UUID sectionId, @NotNull UUID subjectId, @NotBlank String title, String details,
                                  @NotNull LocalDate dueOn) {}

    /**
     * {@code children}: for a parent, which of their children the homework is for. {@code done}: for a student, whether
     * they marked it done; for a parent, whether every child did. {@code doneCount}/{@code classSize}: for the teacher.
     */
    public record HomeworkView(UUID id, UUID sectionId, String section, UUID subjectId, String subject, String teacher,
                               String title, String details, LocalDate dueOn, Instant createdAt, boolean mine,
                               List<String> children, boolean done, int doneCount, int classSize) {}

    // ---- quizzes
    /** kind: CHOICE (pick one answer), TRUEFALSE, or SHORT (a written answer, right when it matches one of {@code accepted}, one per line). */
    public record QuestionInput(@NotBlank String prompt, String kind, List<String> options, int correctIndex, String accepted, String explanation) {}

    public record QuizRequest(@NotNull UUID sectionId, @NotNull UUID subjectId, @NotBlank String title, String sourceMaterial,
                              String sourceLink, @NotNull List<QuestionInput> questions) {}

    /** answers: the chosen option per question (-1 for none); written: the text per question, used by SHORT questions. */
    public record SubmitRequest(@NotNull List<Integer> answers, List<String> written) {}

    /**
     * A quiz in a list. For a student: {@code score}/{@code total} once taken. For the teacher: how many students took
     * it and their average.
     */
    public record QuizSummary(UUID id, UUID sectionId, String section, UUID subjectId, String subject, String teacher,
                              String title, int questions, Instant createdAt, boolean taken, Integer score, Integer total,
                              int attempts, Integer averagePercent, boolean mine) {}

    /** {@code correctIndex} and {@code explanation} are only filled in for the teacher, or after the student has submitted. */
    public record QuestionView(int number, String kind, String prompt, List<String> options, Integer correctIndex, String accepted,
                               String explanation, Integer chosen, String written, Boolean right) {}

    public record AttemptRow(UUID studentId, String student, int score, int total, Instant submittedAt) {}

    public record QuizDetail(QuizSummary quiz, String sourceMaterial, String sourceLink, List<QuestionView> questions,
                             List<AttemptRow> attempts) {}

    // ---- chat
    public record ChatContact(UUID id, String name, String role, String about, int unread, Instant lastAt, String lastText) {}

    public record ChatMessage(UUID id, boolean mine, String sender, String body, Instant at) {}

    public record ChatThread(ChatContact with, List<ChatMessage> messages) {}

    public record ChatSend(@NotBlank String body) {}

    public record Unread(int unread) {}
}

package qa.sijill.sijill.classroom;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.classroom.ClassroomDtos.ChatContact;
import qa.sijill.sijill.classroom.ClassroomDtos.ChatSend;
import qa.sijill.sijill.classroom.ClassroomDtos.ChatThread;
import qa.sijill.sijill.classroom.ClassroomDtos.HomeworkRequest;
import qa.sijill.sijill.classroom.ClassroomDtos.HomeworkView;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizDetail;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizRequest;
import qa.sijill.sijill.classroom.ClassroomDtos.QuizSummary;
import qa.sijill.sijill.classroom.ClassroomDtos.SubmitRequest;
import qa.sijill.sijill.classroom.ClassroomDtos.Unread;
import qa.sijill.sijill.shared.security.CurrentUser;

/** Homework, quizzes and the teacher chat. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ClassroomController {

    private final HomeworkService homework;
    private final QuizService quizzes;
    private final ChatService chat;

    @GetMapping("/homework")
    public List<HomeworkView> homework(CurrentUser user,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return homework.list(user, from, to);
    }

    @PostMapping("/homework")
    public HomeworkView addHomework(CurrentUser user, @Valid @RequestBody HomeworkRequest request) {
        return homework.add(user, request);
    }

    @PostMapping("/homework/{id}/done")
    public HomeworkView homeworkDone(CurrentUser user, @PathVariable UUID id) {
        return homework.setDone(user, id, true);
    }

    @DeleteMapping("/homework/{id}/done")
    public HomeworkView homeworkNotDone(CurrentUser user, @PathVariable UUID id) {
        return homework.setDone(user, id, false);
    }

    @DeleteMapping("/homework/{id}")
    public void removeHomework(CurrentUser user, @PathVariable UUID id) {
        homework.remove(user, id);
    }

    @GetMapping("/quizzes")
    public List<QuizSummary> quizzes(CurrentUser user) {
        return quizzes.list(user);
    }

    @PostMapping("/quizzes")
    public QuizDetail createQuiz(CurrentUser user, @Valid @RequestBody QuizRequest request) {
        return quizzes.create(user, request);
    }

    @GetMapping("/quizzes/{id}")
    public QuizDetail quiz(CurrentUser user, @PathVariable UUID id) {
        return quizzes.detail(user, id);
    }

    @PostMapping("/quizzes/{id}/submit")
    public QuizDetail submitQuiz(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody SubmitRequest request) {
        return quizzes.submit(user, id, request.answers(), request.written());
    }

    @DeleteMapping("/quizzes/{id}")
    public void removeQuiz(CurrentUser user, @PathVariable UUID id) {
        quizzes.remove(user, id);
    }

    @GetMapping("/chat")
    public List<ChatContact> contacts(CurrentUser user) {
        return chat.contacts(user);
    }

    @GetMapping("/chat/unread")
    public Unread unread(CurrentUser user) {
        return new Unread(chat.unreadTotal(user));
    }

    @GetMapping("/chat/{personId}")
    public ChatThread thread(CurrentUser user, @PathVariable UUID personId) {
        return chat.thread(user, personId);
    }

    @PostMapping("/chat/{personId}")
    public ChatThread send(CurrentUser user, @PathVariable UUID personId, @Valid @RequestBody ChatSend request) {
        return chat.send(user, personId, request.body());
    }
}

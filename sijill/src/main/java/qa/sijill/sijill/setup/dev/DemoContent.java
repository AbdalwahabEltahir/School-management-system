package qa.sijill.sijill.setup.dev;

import java.util.List;
import java.util.Locale;

/**
 * <b>Demo only.</b> Realistic classroom content for the demo and test data: homework a teacher would really set,
 * quizzes with real questions, answers and explanations, and study links, for each subject. Subjects are matched by
 * name (Math / Mathematics, English, Science, Arabic); anything else falls back to general tasks.
 */
final class DemoContent {

    private DemoContent() {
    }

    record Question(String prompt, List<String> options, int correct, String explanation) {}

    record Quiz(String title, String material, List<Question> questions) {}

    static String key(String subject) {
        String s = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        if (s.startsWith("math")) return "math";
        if (s.startsWith("eng")) return "english";
        if (s.startsWith("sci")) return "science";
        if (s.startsWith("arab") || s.contains("عرب")) return "arabic";
        return "other";
    }

    /** Homework titles and details, by subject. */
    static List<String[]> homework(String subject) {
        return switch (key(subject)) {
            case "math" -> List.of(
                    new String[] {"Fractions worksheet, questions 1–12", "Simplify each fraction and show your working."},
                    new String[] {"Textbook page 46, exercises 3 to 8", "Adding and subtracting decimals."},
                    new String[] {"Times tables practice (7, 8 and 9)", "Practise for 15 minutes and write the hardest ones in your notebook."},
                    new String[] {"Word problems on percentages", "Solve the five problems on the sheet. One line of working each."},
                    new String[] {"Measure five objects at home", "Write each length in centimetres and in millimetres."});
            case "english" -> List.of(
                    new String[] {"Read chapter 3 of the class reader", "Write three sentences about what the main character wants."},
                    new String[] {"Spelling list, week 6", "Learn the 15 words. Short test on Thursday."},
                    new String[] {"Write a paragraph about your weekend", "8 to 10 sentences. Use the past tense."},
                    new String[] {"Grammar sheet: present perfect", "Complete all twelve sentences."},
                    new String[] {"Vocabulary: animals and habitats", "Match each word to its meaning and use five in sentences."});
            case "science" -> List.of(
                    new String[] {"Label the parts of a plant cell", "Use the diagram on page 22. Colour the chloroplasts green."},
                    new String[] {"States of matter: three examples each", "Give three solids, three liquids and three gases from your home."},
                    new String[] {"Water cycle poster", "A4 size. Show evaporation, condensation and precipitation."},
                    new String[] {"Read pages 30–33 on forces", "Answer questions 1 to 4 at the end of the section."},
                    new String[] {"Observe the moon for three nights", "Draw its shape each night and write the date."});
            case "arabic" -> List.of(
                    new String[] {"قراءة نص «الوطن» وإجابة الأسئلة", "أجب عن أسئلة الفهم في الصفحة 18."},
                    new String[] {"حفظ الأبيات الأربعة الأولى من القصيدة", "التسميع يوم الخميس."},
                    new String[] {"تمارين الإعراب: الفاعل والمفعول به", "أكمل التمارين من 1 إلى 6."},
                    new String[] {"كتابة فقرة عن رحلة مدرسية", "من 8 إلى 10 أسطر مع مراعاة علامات الترقيم."},
                    new String[] {"إملاء: كلمات الهمزة المتوسطة", "تدرّب على كتابة الكلمات العشر."});
            default -> List.of(
                    new String[] {"Revise today's lesson", "Read your notes and write three questions you still have."},
                    new String[] {"Project research", "Find two facts for your group project."});
        };
    }

    /** Two quizzes per subject, each with four real questions, the right answer and why. */
    static List<Quiz> quizzes(String subject) {
        return switch (key(subject)) {
            case "math" -> List.of(
                    new Quiz("Fractions check", "Unit 3: Fractions, textbook pages 40–52", List.of(
                            new Question("What is 1/2 + 1/4?", List.of("3/4", "2/6", "1/6", "2/4"), 0, "1/2 is the same as 2/4, and 2/4 + 1/4 = 3/4."),
                            new Question("Which fraction is the largest?", List.of("1/3", "1/2", "1/4", "1/5"), 1, "With the same top number, the smaller the bottom number, the bigger the piece."),
                            new Question("Simplify 6/8.", List.of("3/4", "2/3", "6/8 cannot be simplified", "1/2"), 0, "Divide the top and the bottom by 2: 6/8 = 3/4."),
                            new Question("What is 3/5 of 20?", List.of("15", "12", "10", "8"), 1, "20 ÷ 5 = 4, and 4 × 3 = 12."))),
                    new Quiz("Decimals and percentages", "Unit 4: Decimals and percentages, pages 54–63", List.of(
                            new Question("What is 0.5 as a percentage?", List.of("5%", "50%", "0.5%", "500%"), 1, "0.5 = 50/100 = 50%."),
                            new Question("Which number is bigger: 0.7 or 0.65?", List.of("0.65", "They are equal", "0.7", "It depends"), 2, "0.7 = 0.70, which is more than 0.65."),
                            new Question("What is 10% of 250?", List.of("2.5", "25", "250", "10"), 1, "10% means one tenth: 250 ÷ 10 = 25."),
                            new Question("2.4 + 1.35 = ?", List.of("3.75", "3.39", "4.75", "3.65"), 0, "Line up the decimal points: 2.40 + 1.35 = 3.75."))));
            case "english" -> List.of(
                    new Quiz("Past tense check", "Grammar unit: the past simple", List.of(
                            new Question("Yesterday I ___ to the park.", List.of("go", "went", "going", "goes"), 1, "'Yesterday' tells us it is the past, so we use 'went'."),
                            new Question("Which word is a verb?", List.of("quickly", "happy", "jump", "blue"), 2, "A verb is an action word. 'Jump' is an action."),
                            new Question("She ___ her homework last night.", List.of("finish", "finishes", "finished", "finishing"), 2, "Regular verbs take -ed in the past: finished."),
                            new Question("Choose the correct plural of 'child'.", List.of("childs", "children", "childes", "child"), 1, "'Child' has an irregular plural: children."))),
                    new Quiz("Reading and vocabulary", "Class reader, chapters 1–3", List.of(
                            new Question("What is the opposite of 'ancient'?", List.of("old", "modern", "huge", "quiet"), 1, "'Ancient' means very old; its opposite is 'modern'."),
                            new Question("Which sentence is correct?", List.of("He don't like milk.", "He doesn't likes milk.", "He doesn't like milk.", "He not like milk."), 2, "With he/she/it we use 'doesn't' + the base verb."),
                            new Question("A 'habitat' is…", List.of("a kind of food", "the place where an animal lives", "a type of weather", "a large animal"), 1, "An animal's habitat is its natural home."),
                            new Question("Which word is spelled correctly?", List.of("becuase", "because", "becouse", "beacause"), 1, "The correct spelling is 'because'."))));
            case "science" -> List.of(
                    new Quiz("Plants and cells", "Science unit 2: Living things, pages 18–29", List.of(
                            new Question("Which part of the plant makes food?", List.of("Roots", "Leaves", "Stem", "Flower"), 1, "Leaves use sunlight to make food: this is photosynthesis."),
                            new Question("What do plants take in from the air?", List.of("Oxygen", "Nitrogen", "Carbon dioxide", "Hydrogen"), 2, "Plants take in carbon dioxide and give out oxygen."),
                            new Question("Which part of a plant cell is green?", List.of("Nucleus", "Chloroplast", "Cell wall", "Vacuole"), 1, "Chloroplasts hold chlorophyll, which is green."),
                            new Question("Roots mainly…", List.of("make seeds", "take in water", "attract insects", "make food"), 1, "Roots hold the plant in place and take in water and minerals."))),
                    new Quiz("States of matter", "Science unit 3: Materials, pages 30–41", List.of(
                            new Question("Water turning into steam is called…", List.of("freezing", "melting", "evaporation", "condensation"), 2, "A liquid turning into a gas is evaporation."),
                            new Question("Which of these is a gas?", List.of("Ice", "Juice", "Oxygen", "Sand"), 2, "Oxygen is a gas at room temperature."),
                            new Question("At what temperature does pure water freeze?", List.of("0 °C", "10 °C", "100 °C", "-10 °C"), 0, "Pure water freezes at 0 °C."),
                            new Question("Which state of matter keeps its own shape?", List.of("Gas", "Liquid", "Solid", "None"), 2, "Solids keep their shape; liquids and gases take the shape of their container."))));
            case "arabic" -> List.of(
                    new Quiz("اختبار قصير: الفاعل والمفعول به", "الوحدة الثالثة: الجملة الفعلية", List.of(
                            new Question("ما الفاعل في جملة: «كتبَ الطالبُ الدرسَ»؟", List.of("كتب", "الطالب", "الدرس", "لا يوجد"), 1, "الفاعل هو من قام بالفعل، وهو «الطالب»."),
                            new Question("ما المفعول به في جملة: «قرأتْ مريمُ القصةَ»؟", List.of("قرأت", "مريم", "القصة", "لا يوجد"), 2, "المفعول به هو ما وقع عليه الفعل: «القصة»."),
                            new Question("علامة رفع الفاعل المفرد هي…", List.of("الفتحة", "الضمة", "الكسرة", "السكون"), 1, "يُرفع الفاعل المفرد بالضمة."),
                            new Question("أيّ الكلمات التالية فعل؟", List.of("مدرسة", "جميل", "يلعب", "في"), 2, "«يلعب» تدل على حدث وزمن، فهي فعل."))),
                    new Quiz("اختبار قصير: الإملاء", "درس الهمزة المتوسطة", List.of(
                            new Question("اختر الكتابة الصحيحة:", List.of("مسألة", "مسئلة", "مسالة", "مسأله"), 0, "الهمزة مفتوحة وما قبلها ساكن، فتُكتب على الألف."),
                            new Question("اختر الكتابة الصحيحة:", List.of("سُأل", "سُئِل", "سُؤل", "سءل"), 1, "الهمزة مكسورة، فتُكتب على النبرة."),
                            new Question("اختر الكتابة الصحيحة:", List.of("رءوس", "رؤوس", "رأوس", "رئوس"), 1, "الهمزة مضمومة، فتُكتب على الواو."),
                            new Question("جمع كلمة «سؤال» هو…", List.of("سؤالات", "أسئلة", "سآئل", "سؤالين"), 1, "جمع «سؤال» هو «أسئلة»."))));
            default -> List.of(new Quiz("Quick review", "This week's lessons", List.of(
                    new Question("How many days are in a week?", List.of("5", "6", "7", "8"), 2, "A week has seven days."),
                    new Question("Which is the capital of Qatar?", List.of("Al Wakrah", "Doha", "Al Khor", "Dukhan"), 1, "Doha is the capital of Qatar."))));
        };
    }

    /** Study links a teacher would share: [title, link]. */
    static List<String[]> materials(String subject) {
        return switch (key(subject)) {
            case "math" -> List.of(new String[] {"Fractions: explained with pictures", "https://www.youtube.com/watch?v=demo-fractions"},
                    new String[] {"Times tables practice sheet", "https://drive.google.com/file/d/demo-times-tables"});
            case "english" -> List.of(new String[] {"Past tense: rules and examples", "https://docs.google.com/document/d/demo-past-tense"},
                    new String[] {"Class reader: chapter 3 audio", "https://drive.google.com/file/d/demo-reader-audio"});
            case "science" -> List.of(new String[] {"The plant cell: labelled diagram", "https://drive.google.com/file/d/demo-plant-cell"},
                    new String[] {"Video: the water cycle", "https://www.youtube.com/watch?v=demo-water-cycle"});
            case "arabic" -> List.of(new String[] {"شرح الفاعل والمفعول به", "https://docs.google.com/presentation/d/demo-arabic-grammar"},
                    new String[] {"تسجيل صوتي للقصيدة", "https://drive.google.com/file/d/demo-arabic-poem"});
            default -> List.<String[]>of(new String[] {"Lesson notes", "https://docs.google.com/document/d/demo-notes"});
        };
    }

    /** Names for demo students (unique within the list). */
    static final List<String> STUDENT_NAMES = List.of(
            "Ahmed Al-Kuwari", "Fatima Al-Thani", "Yousef Al-Sulaiti", "Maryam Al-Marri", "Hamad Al-Naimi", "Aisha Al-Mannai",
            "Khalid Al-Hajri", "Noor Al-Dosari", "Abdullah Al-Kaabi", "Hessa Al-Mohannadi", "Saud Al-Attiyah", "Reem Al-Ansari",
            "Ali Al-Muftah", "Sara Al-Jaber", "Mohammed Al-Shahwani", "Lulwa Al-Misnad", "Tamim Al-Emadi", "Dana Al-Obaidly",
            "Faisal Al-Khulaifi", "Moza Al-Baker", "Nasser Al-Hammadi", "Alanoud Al-Malki", "Jassim Al-Sada", "Shaikha Al-Fardan");
}

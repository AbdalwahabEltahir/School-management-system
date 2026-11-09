package qa.sijill.sijill.rules.check;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.rules.model.CheckType;

/**
 * Looks up the {@link FileCheck} that implements a {@link CheckType}.
 *
 * <p>Fails at startup if two classes claim the same type or if a type has no implementation, so a
 * rule can never reference a check that does not exist.
 */
@Component
public class CheckRegistry {

    private final Map<CheckType, FileCheck> checks = new EnumMap<>(CheckType.class);

    public CheckRegistry(List<FileCheck> implementations) {
        for (FileCheck check : implementations) {
            if (checks.put(check.type(), check) != null) {
                throw new IllegalStateException("Two FileCheck classes implement " + check.type());
            }
        }
        for (CheckType type : CheckType.values()) {
            if (!checks.containsKey(type)) {
                throw new IllegalStateException("No FileCheck class implements " + type);
            }
        }
    }

    public FileCheck get(CheckType type) {
        return checks.get(type);
    }
}

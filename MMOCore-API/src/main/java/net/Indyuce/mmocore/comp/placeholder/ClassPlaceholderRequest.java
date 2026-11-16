package net.Indyuce.mmocore.comp.placeholder;

import java.util.Locale;

/** Parses only the new namespace, leaving existing MMOCore placeholders untouched. */
final class ClassPlaceholderRequest {
    final String classId, metric, key;
    final int rank;

    private ClassPlaceholderRequest(String classId, String metric, String key, int rank) {
        this.classId = classId.toUpperCase(Locale.ROOT);
        this.metric = metric;
        this.key = key;
        this.rank = rank;
    }

    static ClassPlaceholderRequest parse(String raw) {
        if (!raw.startsWith("class_")) return null;
        String body = raw.substring(6);
        if (body.startsWith("top_")) {
            String rest = body.substring(4);
            int last = rest.lastIndexOf('_');
            if (last < 0) return null;
            int separator = rest.lastIndexOf('_', last - 1);
            if (separator < 1) return null;
            String metric = rest.substring(last + 1);
            if (!metric.equals("name") && !metric.equals("level") && !metric.equals("experience")
                    && !metric.equals("exp") && !metric.equals("uuid")) return null;
            try {
                int rank = Integer.parseInt(rest.substring(separator + 1, last));
                return rank > 0 ? new ClassPlaceholderRequest(rest.substring(0, separator), metric, null, rank) : null;
            } catch (NumberFormatException ignored) { return null; }
        }
        // Longer metric names must precede their shorter prefixes.
        for (String metric : new String[]{"attribute_reallocation_points", "attribute_realloc_points",
                "skill_tree_reallocation_points", "skill_reallocation_points", "attribute_points", "skill_points",
                "experience", "level", "exp", "health", "mana", "stamina", "stellium", "attribute", "skill"}) {
            if (!body.startsWith(metric + "_")) continue;
            String rest = body.substring(metric.length() + 1);
            if (rest.isEmpty()) return null;
            if (metric.equals("attribute") || metric.equals("skill")) {
                int separator = rest.indexOf(':');
                if (separator < 1 || separator == rest.length() - 1) return null;
                return new ClassPlaceholderRequest(rest.substring(0, separator), metric, rest.substring(separator + 1), 0);
            }
            return new ClassPlaceholderRequest(rest, metric, null, 0);
        }
        return null;
    }
}

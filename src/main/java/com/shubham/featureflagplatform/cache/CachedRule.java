package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.targeting.Operator;

public record CachedRule(String attribute, Operator operator, String value) {
    public boolean matches(String userValue) {
        if (userValue == null) return false;
        return switch (operator) {
            case EQUALS -> userValue.equals(value);
            case NOT_EQUALS -> !userValue.equals(value);
            case IN -> {
                for (String v: value.split(",")) {
                    if (userValue.equals(v.trim())) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }
}

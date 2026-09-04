package com.shubham.featureflagplatform.targeting;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class TargetingRule {
    @Column (name = "attribute_name") 
    private String attribute;

    @Enumerated (EnumType.STRING)
    @Column (name = "operator")
    private Operator operator;

    @Column (name = "rule_value", length = 1000)
    private String value;

    protected TargetingRule() {}

    public TargetingRule(String attribute, Operator operator, String value) {
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
    }

    public boolean matches(String userValue) {
        if (userValue == null) {
            return false;
        }
        return switch (operator) {
            case EQUALS -> userValue.equals(value);
            case NOT_EQUALS -> !userValue.equals(value);
            case IN -> {
                String[] values = value.split(",");
                boolean matchFound = false;
                for (String val : values) {
                    if (userValue.equals(val.trim())) {
                        matchFound = true;
                        break;
                    }
                }
                yield matchFound;
            }
        };
    }

    public String getAttribute() {
        return attribute;
    }
    public Operator getOperator() {
        return operator;
    }
    public String getValue() {
        return value;
    }
}

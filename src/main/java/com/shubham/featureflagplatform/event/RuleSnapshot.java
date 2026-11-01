package com.shubham.featureflagplatform.event;

import com.shubham.featureflagplatform.targeting.Operator;

public record RuleSnapshot(String attribute, Operator operator, String value) {}

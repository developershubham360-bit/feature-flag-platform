package com.shubham.featureflagplatform.evaluation;

import com.shubham.featureflagplatform.evaluation.dto.EvaluationRequest;
import com.shubham.featureflagplatform.evaluation.dto.EvaluationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluate")
public class EvaluationController {
    
    private final EvaluationService evaluationService;
    
    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluationResponse evaluate(@RequestBody @Valid EvaluationRequest req) {
        return evaluationService.evaluate(req);
    }
}

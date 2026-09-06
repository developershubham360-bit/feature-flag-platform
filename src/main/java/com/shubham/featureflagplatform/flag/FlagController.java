package com.shubham.featureflagplatform.flag;

import com.shubham.featureflagplatform.flag.dto.CreateFlagRequest;
import com.shubham.featureflagplatform.flag.dto.FlagDto;
import com.shubham.featureflagplatform.flag.dto.UpdateFlagRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/flags")
public class FlagController {
    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @GetMapping
    public List<FlagDto> getAllFlags() {
        return flagService.findAll();
    }

    @GetMapping("/{id}")
    public FlagDto getFlagById(@PathVariable Long id) {
        return flagService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlagDto create(@Valid @RequestBody CreateFlagRequest req) {
        return flagService.create(req);
    }

    @PutMapping("/{id}")
    public FlagDto update(@PathVariable Long id, @Valid @RequestBody UpdateFlagRequest req) {
        return flagService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        flagService.delete(id);
    }
}

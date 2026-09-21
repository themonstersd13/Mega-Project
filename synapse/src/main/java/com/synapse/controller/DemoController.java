package com.synapse.controller;

import com.synapse.demo.DemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {
    private final DemoService demoService;

    @PostMapping("/seed")
    public ResponseEntity<DemoService.DemoSeedResponse> seed() {
        return ResponseEntity.ok(demoService.seedDemoWorkspace());
    }

    @PostMapping("/evaluate")
    public ResponseEntity<DemoService.DemoEvaluationResponse> evaluate(@RequestParam(defaultValue = "50") int runs) {
        return ResponseEntity.ok(demoService.evaluateDemo(runs));
    }

    @GetMapping("/report")
    public ResponseEntity<DemoService.DemoEvaluationResponse> report() {
        return ResponseEntity.ok(demoService.buildReport());
    }
}

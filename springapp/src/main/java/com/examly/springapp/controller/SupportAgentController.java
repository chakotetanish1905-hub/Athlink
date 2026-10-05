package com.examly.springapp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.examly.springapp.model.SupportAgent;
import com.examly.springapp.service.SupportAgentService;

import jakarta.validation.Valid;

// Role rules (Manager only, except GET by id) are in SecurityConfig.
@RestController
@RequestMapping("/api/supportAgent")
public class SupportAgentController {

    private final SupportAgentService supportAgentService;

    public SupportAgentController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    // Manager: 201 with the new agent, 409 if the email is already used
    @PostMapping
    public ResponseEntity<SupportAgent> addSupportAgent(@Valid @RequestBody SupportAgent supportAgent) {
        SupportAgent savedAgent = supportAgentService.addSupportAgent(supportAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedAgent);
    }

    // Manager and Client: 200 with the agent, 404 if not found
    @GetMapping("/{agentId}")
    public ResponseEntity<SupportAgent> getSupportAgentById(@PathVariable Long agentId) {
        SupportAgent agent = supportAgentService.getSupportAgentById(agentId).orElse(null);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(agent);
    }

    // Manager: 200 with all agents, 204 when there are none
    @GetMapping
    public ResponseEntity<List<SupportAgent>> getAllSupportAgents() {
        List<SupportAgent> agents = supportAgentService.getAllSupportAgents();
        if (agents.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(agents);
    }

    // Manager: 200 with the updated agent
    @PutMapping("/{agentId}")
    public ResponseEntity<SupportAgent> updateSupportAgent(@PathVariable Long agentId,
            @Valid @RequestBody SupportAgent supportAgent) {
        SupportAgent updatedAgent = supportAgentService.updateSupportAgent(agentId, supportAgent);
        return ResponseEntity.ok(updatedAgent);
    }

    // Manager: 200 with the deleted agent
    @DeleteMapping("/{agentId}")
    public ResponseEntity<SupportAgent> deleteSupportAgent(@PathVariable Long agentId) {
        SupportAgent deletedAgent = supportAgentService.deleteSupportAgent(agentId);
        return ResponseEntity.ok(deletedAgent);
    }
}

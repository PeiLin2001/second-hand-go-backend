package com.example.HellTrain.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/edu")
public class EduController {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/schools")
    public ResponseEntity<String> getSchools() {
        String url = "https://stats.moe.gov.tw/files/opendata/u1_new.json";

        String result = restTemplate.getForObject(url, String.class);

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }
}
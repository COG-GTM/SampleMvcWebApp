package com.northwind.assessment.scheduling.service;

public record BookingCommand(Integer sessionId, String candidateNumber, String accommodationCode, String externalRef,
                             String channel) {
}

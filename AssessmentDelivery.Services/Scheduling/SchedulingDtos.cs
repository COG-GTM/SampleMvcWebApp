using System;
using System.Collections.Generic;
using Newtonsoft.Json;

namespace AssessmentDelivery.Services.Scheduling
{
    //Wire shapes of docs/to-be/scheduling-service-openapi.yaml. Timestamps are centre-local with no offset (HB-25).

    public class ApiSession
    {
        [JsonProperty("sessionId")] public int SessionId { get; set; }
        [JsonProperty("examId")] public int ExamId { get; set; }
        [JsonProperty("examCode")] public string ExamCode { get; set; }
        [JsonProperty("examTitle")] public string ExamTitle { get; set; }
        [JsonProperty("durationMinutes")] public int? DurationMinutes { get; set; }
        [JsonProperty("testCenterId")] public int TestCenterId { get; set; }
        [JsonProperty("centerCode")] public string CenterCode { get; set; }
        [JsonProperty("centerName")] public string CenterName { get; set; }
        [JsonProperty("city")] public string City { get; set; }
        [JsonProperty("roomId")] public int RoomId { get; set; }
        [JsonProperty("roomName")] public string RoomName { get; set; }
        [JsonProperty("startsAt")] public DateTime StartsAt { get; set; }
        [JsonProperty("capacity")] public int Capacity { get; set; }
        [JsonProperty("accommodationCapacity")] public int AccommodationCapacity { get; set; }
        [JsonProperty("seatsAvailable")] public int SeatsAvailable { get; set; }
        [JsonProperty("status")] public string Status { get; set; }
        [JsonProperty("bookedSeats")] public int? BookedSeats { get; set; }
        [JsonProperty("waitlistCount")] public int? WaitlistCount { get; set; }
    }

    public class ApiSessionSummary
    {
        [JsonProperty("testCenterId")] public int TestCenterId { get; set; }
        [JsonProperty("sessionCount")] public int SessionCount { get; set; }
        [JsonProperty("openSessionCount")] public int OpenSessionCount { get; set; }
    }

    public class ApiBooking
    {
        [JsonProperty("bookingId")] public int BookingId { get; set; }
        [JsonProperty("sessionId")] public int SessionId { get; set; }
        [JsonProperty("candidateId")] public int CandidateId { get; set; }
        [JsonProperty("candidateNumber")] public string CandidateNumber { get; set; }
        [JsonProperty("examId")] public int? ExamId { get; set; }
        [JsonProperty("examCode")] public string ExamCode { get; set; }
        [JsonProperty("examTitle")] public string ExamTitle { get; set; }
        [JsonProperty("centerName")] public string CenterName { get; set; }
        [JsonProperty("sessionStartsAt")] public DateTime SessionStartsAt { get; set; }
        [JsonProperty("status")] public string Status { get; set; }
        [JsonProperty("accommodationCode")] public string AccommodationCode { get; set; }
        [JsonProperty("channel")] public string Channel { get; set; }
        [JsonProperty("externalRef")] public string ExternalRef { get; set; }
        [JsonProperty("bookedAt")] public DateTime BookedAt { get; set; }
        [JsonProperty("cancelledAt")] public DateTime? CancelledAt { get; set; }
        [JsonProperty("cancelReason")] public string CancelReason { get; set; }
        [JsonProperty("waitlistPosition")] public int? WaitlistPosition { get; set; }
    }

    public class ApiBookRequest
    {
        [JsonProperty("sessionId")] public int SessionId { get; set; }
        [JsonProperty("candidateId")] public int CandidateId { get; set; }
        [JsonProperty("accommodationCode")] public string AccommodationCode { get; set; }
        [JsonProperty("channel")] public string Channel { get; set; }
    }

    public class ApiBookingResult
    {
        [JsonProperty("bookingId")] public int BookingId { get; set; }
        [JsonProperty("status")] public string Status { get; set; }
        [JsonProperty("waitlistPosition")] public int? WaitlistPosition { get; set; }
        [JsonProperty("cancelledBookingId")] public int? CancelledBookingId { get; set; }
    }

    public class ApiCapacityChangeResult
    {
        [JsonProperty("sessionId")] public int SessionId { get; set; }
        [JsonProperty("oldCapacity")] public int OldCapacity { get; set; }
        [JsonProperty("newCapacity")] public int NewCapacity { get; set; }
        [JsonProperty("seatsAvailable")] public int SeatsAvailable { get; set; }
        [JsonProperty("demotedBookingIds")] public List<int> DemotedBookingIds { get; set; }
    }

    public class ApiCapacityAuditRow
    {
        [JsonProperty("auditId")] public long AuditId { get; set; }
        [JsonProperty("loggedAt")] public DateTime LoggedAt { get; set; }
        [JsonProperty("actor")] public string Actor { get; set; }
        [JsonProperty("sessionId")] public int SessionId { get; set; }
        [JsonProperty("examCode")] public string ExamCode { get; set; }
        [JsonProperty("sessionStartsAt")] public DateTime SessionStartsAt { get; set; }
        [JsonProperty("roomName")] public string RoomName { get; set; }
        [JsonProperty("currentCapacity")] public int CurrentCapacity { get; set; }
        [JsonProperty("currentSeatsAvailable")] public int CurrentSeatsAvailable { get; set; }
        [JsonProperty("detail")] public string Detail { get; set; }
    }

    public class ApiCapacityAuditPage
    {
        [JsonProperty("totalRows")] public int TotalRows { get; set; }
        [JsonProperty("rows")] public List<ApiCapacityAuditRow> Rows { get; set; }
    }

    public class ApiRosterLine
    {
        [JsonProperty("candidateNumber")] public string CandidateNumber { get; set; }
        [JsonProperty("lastName")] public string LastName { get; set; }
        [JsonProperty("firstName")] public string FirstName { get; set; }
        [JsonProperty("email")] public string Email { get; set; }
        [JsonProperty("bookingStatus")] public string BookingStatus { get; set; }
        [JsonProperty("accommodationCode")] public string AccommodationCode { get; set; }
        [JsonProperty("channel")] public string Channel { get; set; }
        [JsonProperty("bookedAt")] public DateTime BookedAt { get; set; }
        [JsonProperty("waitlistPosition")] public int? WaitlistPosition { get; set; }
    }

    public class ApiRoster
    {
        [JsonProperty("sessionId")] public int SessionId { get; set; }
        [JsonProperty("centerCode")] public string CenterCode { get; set; }
        [JsonProperty("examCode")] public string ExamCode { get; set; }
        [JsonProperty("startsAt")] public DateTime StartsAt { get; set; }
        [JsonProperty("lines")] public List<ApiRosterLine> Lines { get; set; }
    }

    public class ApiReconcilerRun
    {
        [JsonProperty("runId")] public int RunId { get; set; }
        [JsonProperty("trigger")] public string Trigger { get; set; }
        [JsonProperty("status")] public string Status { get; set; }
        [JsonProperty("startedAt")] public DateTime StartedAt { get; set; }
        [JsonProperty("finishedAt")] public DateTime? FinishedAt { get; set; }
        [JsonProperty("checked")] public int Checked { get; set; }
        [JsonProperty("updated")] public int Updated { get; set; }
        [JsonProperty("skipped")] public int Skipped { get; set; }
        [JsonProperty("error")] public string Error { get; set; }
        [JsonProperty("actor")] public string Actor { get; set; }
    }

    public class ApiError
    {
        [JsonProperty("error")] public string Error { get; set; }
        [JsonProperty("message")] public string Message { get; set; }
        [JsonProperty("legacyState")] public int? LegacyState { get; set; }
    }
}

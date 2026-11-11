using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Booking;

namespace AssessmentDelivery.Web.Models
{
    public class FindSessionsViewModel
    {
        public string ExamCode { get; set; }
        public string CenterCode { get; set; }
        [DataType(DataType.Date)]
        public DateTime? From { get; set; }
        [DataType(DataType.Date)]
        public DateTime? To { get; set; }
        public string CandidateNumber { get; set; }

        public IList<Exam> Exams { get; set; }
        public IList<TestCenter> Centers { get; set; }
        public IList<SessionListItem> Results { get; set; }
        public bool Searched { get; set; }
    }

    public class BookViewModel
    {
        public int SessionId { get; set; }
        public SessionListItem Session { get; set; }

        [Required]
        [Display(Name = "Candidate number")]
        public string CandidateNumber { get; set; }

        [Display(Name = "Accommodation code")]
        public string AccommodationCode { get; set; }
    }

    public class MyBookingsViewModel
    {
        public string CandidateNumber { get; set; }
        public Candidate Candidate { get; set; }
        public IList<BookingListItem> Bookings { get; set; }
    }

    public class CancelViewModel
    {
        public BookingListItem Booking { get; set; }
        public string CandidateNumber { get; set; }
        [StringLength(200)]
        public string Reason { get; set; }
    }

    public class RescheduleViewModel
    {
        public BookingListItem Booking { get; set; }
        public string CandidateNumber { get; set; }
        public IList<SessionListItem> Targets { get; set; }
        public int? NewSessionId { get; set; }
    }
}

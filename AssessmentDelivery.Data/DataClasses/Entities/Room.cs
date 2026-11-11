using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("Room")]
    public class Room
    {
        public int RoomId { get; set; }
        public int TestCenterId { get; set; }

        [Required, StringLength(50)]
        public string RoomName { get; set; }

        public int SeatCount { get; set; }
        public int AccommodationSeats { get; set; }
        public bool IsActive { get; set; }

        public virtual TestCenter TestCenter { get; set; }
    }
}

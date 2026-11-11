namespace AssessmentDelivery.Services.Admin
{
    public class CenterListItem
    {
        public int TestCenterId { get; set; }
        public string CenterCode { get; set; }
        public string Name { get; set; }
        public string City { get; set; }
        public string Region { get; set; }
        public string TimeZone { get; set; }
        public bool IsActive { get; set; }
        public int RoomCount { get; set; }
        public int SessionCount { get; set; }
        public int OpenSessionCount { get; set; }
    }
}

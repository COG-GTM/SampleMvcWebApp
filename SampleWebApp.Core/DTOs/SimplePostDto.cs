namespace SampleWebApp.Core.DTOs
{
    public class SimplePostDto
    {
        public int PostId { get; set; }
        public int BlogId { get; set; }
        public string BloggerName { get; set; } = string.Empty;
        public string Title { get; set; } = string.Empty;
        public DateTime LastUpdated { get; set; }
        public DateTime LastUpdatedUtc => DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc);
        public ICollection<string> TagNames { get; set; } = new List<string>();
    }
}

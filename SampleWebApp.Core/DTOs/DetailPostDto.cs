namespace SampleWebApp.Core.DTOs
{
    public class DetailPostDto
    {
        public int PostId { get; set; }
        public string Title { get; set; } = string.Empty;
        public string Content { get; set; } = string.Empty;
        public string BloggerName { get; set; } = string.Empty;
        public int BlogId { get; set; }
        public DateTime LastUpdated { get; set; }
        public DateTime LastUpdatedUtc => DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc);
        public ICollection<TagDto> Tags { get; set; } = new List<TagDto>();
    }
}

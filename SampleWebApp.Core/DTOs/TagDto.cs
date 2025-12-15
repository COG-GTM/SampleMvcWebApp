namespace SampleWebApp.Core.DTOs
{
    public class TagDto
    {
        public int TagId { get; set; }
        public string Name { get; set; } = string.Empty;
        public string Slug { get; set; } = string.Empty;
        public int PostCount { get; set; }
    }
}

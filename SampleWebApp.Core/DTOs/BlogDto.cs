namespace SampleWebApp.Core.DTOs
{
    public class BlogDto
    {
        public int BlogId { get; set; }
        public string Name { get; set; } = string.Empty;
        public string EmailAddress { get; set; } = string.Empty;
        public int PostsCount { get; set; }
    }
}

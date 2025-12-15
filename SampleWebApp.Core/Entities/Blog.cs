using System.ComponentModel.DataAnnotations;

namespace SampleWebApp.Core.Entities
{
    public class Blog
    {
        public int BlogId { get; set; }

        [MinLength(2)]
        [MaxLength(64)]
        [Required]
        public string Name { get; set; } = string.Empty;

        [MaxLength(256)]
        [Required]
        [EmailAddress]
        public string EmailAddress { get; set; } = string.Empty;

        public ICollection<Post> Posts { get; set; } = new List<Post>();
    }
}

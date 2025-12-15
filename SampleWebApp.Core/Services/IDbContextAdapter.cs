using System.Collections.Generic;
using System.Threading.Tasks;

namespace SampleWebApp.Core.Services
{
    public interface IDbContextAdapter
    {
        // Post operations
        Task<IEnumerable<PostEntity>> GetPostsWithIncludesAsync();
        Task<IEnumerable<PostEntity>> GetPostsByBlogIdAsync(int blogId);
        Task<PostEntity> GetPostByIdAsync(int id);
        Task<int> CreatePostAsync(string title, string content, int blogId, int[] tagIds);
        Task UpdatePostAsync(int postId, string title, string content, int blogId, int[] tagIds);
        Task DeletePostAsync(int id);

        // Tag operations
        Task<IEnumerable<TagEntity>> GetAllTagsAsync();
        Task<TagEntity> GetTagByIdAsync(int id);
        Task<TagEntity> GetTagBySlugAsync(string slug);
        Task<int> CreateTagAsync(string name, string slug);
        Task UpdateTagAsync(int tagId, string name, string slug);
        Task DeleteTagAsync(int id);

        // Blog operations
        Task<IEnumerable<BlogEntity>> GetAllBlogsAsync();
        Task<BlogEntity> GetBlogByIdAsync(int id);
        Task<int> CreateBlogAsync(string name, string emailAddress);
        Task UpdateBlogAsync(int blogId, string name, string emailAddress);
        Task DeleteBlogAsync(int id);
    }

    public class PostEntity
    {
        public int PostId { get; set; }
        public string Title { get; set; }
        public string Content { get; set; }
        public int BlogId { get; set; }
        public string BloggerName { get; set; }
        public System.DateTime LastUpdated { get; set; }
        public ICollection<TagEntity> Tags { get; set; }
    }

    public class TagEntity
    {
        public int TagId { get; set; }
        public string Name { get; set; }
        public string Slug { get; set; }
        public int PostsCount { get; set; }
    }

    public class BlogEntity
    {
        public int BlogId { get; set; }
        public string Name { get; set; }
        public string EmailAddress { get; set; }
        public int PostsCount { get; set; }
    }
}

namespace ServiceLayer.PostServices
{
    public static class SimplePostQueryExtensions
    {
        /// <summary>
        /// Filters the posts to the given blog. A null or zero blogId returns the query unchanged.
        /// </summary>
        public static IQueryable<SimplePostDto> FilterByBlogId(this IQueryable<SimplePostDto> posts, int? blogId)
        {
            return blogId == null || blogId == 0 ? posts : posts.Where(x => x.BlogId == blogId);
        }

        /// <summary>
        /// Filters the posts to the given blog. A null or zero blogId returns the query unchanged.
        /// </summary>
        public static IQueryable<SimplePostDtoAsync> FilterByBlogId(this IQueryable<SimplePostDtoAsync> posts, int? blogId)
        {
            return blogId == null || blogId == 0 ? posts : posts.Where(x => x.BlogId == blogId);
        }
    }
}

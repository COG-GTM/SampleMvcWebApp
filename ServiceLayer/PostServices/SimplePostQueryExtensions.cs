namespace ServiceLayer.PostServices
{
    public static class SimplePostQueryExtensions
    {
        public static IQueryable<SimplePostDto> FilterByBlogId(this IQueryable<SimplePostDto> query, int? blogId)
        {
            return blogId == null || blogId == 0 ? query : query.Where(x => x.BlogId == blogId);
        }

        public static IQueryable<SimplePostDtoAsync> FilterByBlogId(this IQueryable<SimplePostDtoAsync> query, int? blogId)
        {
            return blogId == null || blogId == 0 ? query : query.Where(x => x.BlogId == blogId);
        }
    }
}

using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.UiClasses;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class DetailPostService : IDetailPostService
    {
        private readonly SampleWebAppDb _db;
        private readonly IServiceProvider _serviceProvider;

        public IStatusGeneric Status { get; private set; } = new StatusGenericHandler();

        public DetailPostService(SampleWebAppDb db, IServiceProvider serviceProvider)
        {
            _db = db;
            _serviceProvider = serviceProvider;
        }

        public DetailPostDto GetDetail(int postId)
        {
            var crudServices = _serviceProvider.GetRequiredService<ICrudServices>();
            var dto = crudServices.ReadSingle<DetailPostDto>(postId);
            Status = crudServices;
            return dto;
        }

        public DetailPostDto GetNew()
        {
            var dto = new DetailPostDto();
            SetupSecondaryData(dto, null, new List<int>());
            Status = new StatusGenericHandler();
            return dto;
        }

        public DetailPostDto GetForEdit(int postId)
        {
            var dto = GetDetail(postId);
            if (dto != null)
                SetupSecondaryData(dto, dto.BlogId.ToString("D"), dto.Tags.Select(x => x.TagId));
            return dto;
        }

        public DetailPostDto ResetDto(DetailPostDto dto)
        {
            SetupSecondaryData(dto, dto.Bloggers?.SelectedValue,
                PostServiceHelpers.SelectedTagIds(dto.UserChosenTags));
            Status = new StatusGenericHandler();
            return dto;
        }

        public IStatusGeneric Create(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();
            SetupRestOfDto(dto, status);
            if (!status.IsValid)
                return status;

            var post = new Post
            {
                Title = dto.Title,
                Content = dto.Content,
                BlogId = dto.BlogId,
                Tags = dto.Tags
            };
            _db.Posts.Add(post);
            if (!SaveWithChecking(status))
                return status;

            dto.PostId = post.PostId;
            status.Message = "Successfully created Post.";
            return status;
        }

        public IStatusGeneric Update(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();
            var post = _db.Posts.Include(p => p.Tags).SingleOrDefault(p => p.PostId == dto.PostId);
            if (post == null)
                return status.AddError("Could not find the Post you asked for.");

            SetupRestOfDto(dto, status);
            if (!status.IsValid)
                return status;

            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = dto.BlogId;
            post.Tags.Clear();
            foreach (var tag in dto.Tags)
                post.Tags.Add(tag);
            if (!SaveWithChecking(status))
                return status;

            status.Message = "Successfully updated Post.";
            return status;
        }

        //---------------------------------------------------
        //private helpers

        private void SetupSecondaryData(DetailPostDto dto, string selectedBlogger, IEnumerable<int> selectedTagIds)
        {
            dto.Bloggers ??= new DropDownListType();
            dto.UserChosenTags ??= new MultiSelectListType();

            var bloggers = _db.Blogs.AsNoTracking().Select(x => new { x.Name, x.BlogId }).ToList();
            var tags = _db.Tags.AsNoTracking().Select(x => new { x.Name, x.TagId }).ToList();
            PostServiceHelpers.SetupLists(dto.Bloggers, dto.UserChosenTags,
                bloggers.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                tags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)),
                selectedBlogger, selectedTagIds);
        }

        private void SetupRestOfDto(DetailPostDto dto, StatusGenericHandler status)
        {
            //now we sort out the blogger
            var errMsg = SetBloggerIdFromDropDownList(dto);
            if (errMsg != null)
                status.AddError(errMsg, nameof(DetailPostDto.Bloggers));

            //now we sort out the tags
            errMsg = ChangeTagsBasedOnMultiSelectList(dto);
            if (errMsg != null)
                status.AddError(errMsg, nameof(DetailPostDto.UserChosenTags));
        }

        private string SetBloggerIdFromDropDownList(DetailPostDto dto)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            if (blogId == null)
                return PostServiceHelpers.BloggerNotSelected;

            if (_db.Blogs.Find((int)blogId) == null)
                return PostServiceHelpers.BloggerNotFound;

            dto.BlogId = (int)blogId;
            return null;
        }

        private string ChangeTagsBasedOnMultiSelectList(DetailPostDto dto)
        {
            var requiredTagIds = dto.UserChosenTags?.GetFinalSelectionAsInts() ?? Array.Empty<int>();
            if (!requiredTagIds.Any())
                return PostServiceHelpers.NoTagSelected;

            var newTagsForPost = _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToList();
            if (newTagsForPost.Count != requiredTagIds.Distinct().Count())
                return PostServiceHelpers.TagNotFound;

            dto.Tags = newTagsForPost;
            return null;
        }

        private bool SaveWithChecking(StatusGenericHandler status)
        {
            status.CombineStatuses(_db.SaveChangesWithChecking());
            if (!status.IsValid)
                _db.ChangeTracker.Clear();
            return status.IsValid;
        }
    }
}

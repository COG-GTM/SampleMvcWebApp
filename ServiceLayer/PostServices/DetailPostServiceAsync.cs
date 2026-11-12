using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.UiClasses;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class DetailPostServiceAsync : IDetailPostServiceAsync
    {
        private readonly SampleWebAppDb _db;
        private readonly IServiceProvider _serviceProvider;

        public IStatusGeneric Status { get; private set; } = new StatusGenericHandler();

        public DetailPostServiceAsync(SampleWebAppDb db, IServiceProvider serviceProvider)
        {
            _db = db;
            _serviceProvider = serviceProvider;
        }

        public async Task<DetailPostDtoAsync> GetDetailAsync(int postId)
        {
            var crudServices = _serviceProvider.GetRequiredService<ICrudServicesAsync>();
            var dto = await crudServices.ReadSingleAsync<DetailPostDtoAsync>(postId);
            Status = crudServices;
            return dto;
        }

        public async Task<DetailPostDtoAsync> GetNewAsync()
        {
            var dto = new DetailPostDtoAsync();
            await SetupSecondaryDataAsync(dto, null, new List<int>());
            Status = new StatusGenericHandler();
            return dto;
        }

        public async Task<DetailPostDtoAsync> GetForEditAsync(int postId)
        {
            var dto = await GetDetailAsync(postId);
            if (dto != null)
                await SetupSecondaryDataAsync(dto, dto.BlogId.ToString("D"), dto.Tags.Select(x => x.TagId));
            return dto;
        }

        public async Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto)
        {
            await SetupSecondaryDataAsync(dto, dto.Bloggers?.SelectedValue,
                PostServiceHelpers.SelectedTagIds(dto.UserChosenTags));
            Status = new StatusGenericHandler();
            return dto;
        }

        public async Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();
            await SetupRestOfDtoAsync(dto, status);
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
            if (!await SaveWithCheckingAsync(status))
                return status;

            dto.PostId = post.PostId;
            status.Message = "Successfully created Post.";
            return status;
        }

        public async Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();
            var post = await _db.Posts.Include(p => p.Tags).SingleOrDefaultAsync(p => p.PostId == dto.PostId);
            if (post == null)
                return status.AddError("Could not find the Post you asked for.");

            await SetupRestOfDtoAsync(dto, status);
            if (!status.IsValid)
                return status;

            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = dto.BlogId;
            post.Tags.Clear();
            foreach (var tag in dto.Tags)
                post.Tags.Add(tag);
            if (!await SaveWithCheckingAsync(status))
                return status;

            status.Message = "Successfully updated Post.";
            return status;
        }

        //---------------------------------------------------
        //private helpers

        private async Task SetupSecondaryDataAsync(DetailPostDtoAsync dto, string selectedBlogger, IEnumerable<int> selectedTagIds)
        {
            dto.Bloggers ??= new DropDownListType();
            dto.UserChosenTags ??= new MultiSelectListType();

            var bloggers = await _db.Blogs.AsNoTracking().Select(x => new { x.Name, x.BlogId }).ToListAsync();
            var tags = await _db.Tags.AsNoTracking().Select(x => new { x.Name, x.TagId }).ToListAsync();
            PostServiceHelpers.SetupLists(dto.Bloggers, dto.UserChosenTags,
                bloggers.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                tags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)),
                selectedBlogger, selectedTagIds);
        }

        private async Task SetupRestOfDtoAsync(DetailPostDtoAsync dto, StatusGenericHandler status)
        {
            //now we sort out the blogger
            var errMsg = await SetBloggerIdFromDropDownListAsync(dto);
            if (errMsg != null)
                status.AddError(errMsg, nameof(DetailPostDtoAsync.Bloggers));

            //now we sort out the tags
            errMsg = await ChangeTagsBasedOnMultiSelectListAsync(dto);
            if (errMsg != null)
                status.AddError(errMsg, nameof(DetailPostDtoAsync.UserChosenTags));
        }

        private async Task<string> SetBloggerIdFromDropDownListAsync(DetailPostDtoAsync dto)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            if (blogId == null)
                return PostServiceHelpers.BloggerNotSelected;

            if (await _db.Blogs.FindAsync((int)blogId) == null)
                return PostServiceHelpers.BloggerNotFound;

            dto.BlogId = (int)blogId;
            return null;
        }

        private async Task<string> ChangeTagsBasedOnMultiSelectListAsync(DetailPostDtoAsync dto)
        {
            var requiredTagIds = dto.UserChosenTags?.GetFinalSelectionAsInts() ?? Array.Empty<int>();
            if (!requiredTagIds.Any())
                return PostServiceHelpers.NoTagSelected;

            var newTagsForPost = await _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToListAsync();
            if (newTagsForPost.Count != requiredTagIds.Distinct().Count())
                return PostServiceHelpers.TagNotFound;

            dto.Tags = newTagsForPost;
            return null;
        }

        private async Task<bool> SaveWithCheckingAsync(StatusGenericHandler status)
        {
            status.CombineStatuses(await _db.SaveChangesWithCheckingAsync());
            if (!status.IsValid)
                _db.ChangeTracker.Clear();
            return status.IsValid;
        }
    }
}

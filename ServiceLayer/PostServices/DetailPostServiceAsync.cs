using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class DetailPostServiceAsync : DetailPostServiceBase<DetailPostDtoAsync>, IDetailPostServiceAsync
    {
        private readonly ICrudServicesAsync _crudServices;

        public DetailPostServiceAsync(SampleWebAppDb db, ICrudServicesAsync crudServices) : base(db)
        {
            _crudServices = crudServices;
        }

        public Task<DetailPostDtoAsync> GetDetailAsync(int postId)
        {
            return ReadAndSetupAsync(postId);
        }

        public async Task<DetailPostDtoAsync> GetNewAsync()
        {
            Status = new StatusGenericHandler();
            var dto = new DetailPostDtoAsync();
            SetupSecondaryData(dto, await BlogsForList().ToListAsync(), await TagsForList().ToListAsync(),
                null, Enumerable.Empty<int>());
            return dto;
        }

        public Task<DetailPostDtoAsync> GetForEditAsync(int postId)
        {
            return ReadAndSetupAsync(postId);
        }

        public async Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto)
        {
            SetupSecondaryData(dto, await BlogsForList().ToListAsync(), await TagsForList().ToListAsync(),
                SelectedBlogValueFromUser(dto), SelectedTagIdsFromUser(dto));
            return dto;
        }

        public async Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();
            var tags = await CheckSelectionsAsync(dto, status);
            if (!status.IsValid)
                return status;

            var post = new Post { Tags = new List<Tag>() };
            CopyDtoToPost(dto, post);
            ReplaceTags(post, tags);
            Db.Posts.Add(post);
            await SaveAndSetMessageAsync(status, "Successfully created a Post");
            if (status.IsValid)
                dto.PostId = post.PostId;
            return status;
        }

        public async Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();
            var post = await Db.Posts.Include(p => p.Tags).SingleOrDefaultAsync(p => p.PostId == dto.PostId);
            if (post == null)
                return PostNotFound(status);

            var tags = await CheckSelectionsAsync(dto, status);
            if (!status.IsValid)
                return status;

            CopyDtoToPost(dto, post);
            ReplaceTags(post, tags);
            await SaveAndSetMessageAsync(status, "Successfully updated the Post");
            return status;
        }

        //---------------------------------------------------
        //private helpers

        private async Task<DetailPostDtoAsync> ReadAndSetupAsync(int postId)
        {
            var dto = await _crudServices.ReadSingleAsync<DetailPostDtoAsync>(postId);
            var status = new StatusGenericHandler();
            Status = status;
            if (dto == null)
            {
                PostNotFound(status);
                return null;
            }
            SetupSecondaryData(dto, await BlogsForList().ToListAsync(), await TagsForList().ToListAsync(),
                SelectedBlogValueForExisting(dto), SelectedTagIdsForExisting(dto));
            return dto;
        }

        private async Task<List<Tag>> CheckSelectionsAsync(DetailPostDtoAsync dto, StatusGenericHandler status)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            var bloggerExists = blogId != null && await Db.Blogs.AnyAsync(x => x.BlogId == blogId);
            var tagIds = GetUserTagIds(dto);
            var foundTags = await Db.Tags.Where(x => tagIds.Contains(x.TagId)).ToListAsync();
            return SetupRestOfDto(dto, status, bloggerExists, foundTags, tagIds);
        }

        private async Task SaveAndSetMessageAsync(StatusGenericHandler status, string successMessage)
        {
            status.CombineStatuses(await Db.SaveChangesWithCheckingAsync());
            if (status.IsValid)
                status.Message = successMessage;
            else
                Db.ChangeTracker.Clear();
        }
    }
}

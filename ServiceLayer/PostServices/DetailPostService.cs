using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class DetailPostService : DetailPostServiceBase<DetailPostDto>, IDetailPostService
    {
        private readonly ICrudServices _crudServices;

        public DetailPostService(SampleWebAppDb db, ICrudServices crudServices) : base(db)
        {
            _crudServices = crudServices;
        }

        public DetailPostDto GetDetail(int postId)
        {
            return ReadAndSetup(postId);
        }

        public DetailPostDto GetNew()
        {
            Status = new StatusGenericHandler();
            var dto = new DetailPostDto();
            SetupSecondaryData(dto, BlogsForList().ToList(), TagsForList().ToList(), null, Enumerable.Empty<int>());
            return dto;
        }

        public DetailPostDto GetForEdit(int postId)
        {
            return ReadAndSetup(postId);
        }

        public DetailPostDto ResetDto(DetailPostDto dto)
        {
            SetupSecondaryData(dto, BlogsForList().ToList(), TagsForList().ToList(),
                SelectedBlogValueFromUser(dto), SelectedTagIdsFromUser(dto));
            return dto;
        }

        public IStatusGeneric Create(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();
            var tags = CheckSelections(dto, status);
            if (!status.IsValid)
                return status;

            var post = new Post { Tags = new List<Tag>() };
            CopyDtoToPost(dto, post);
            ReplaceTags(post, tags);
            Db.Posts.Add(post);
            return SaveAndSetMessage(status, "Successfully created a Post", () => dto.PostId = post.PostId);
        }

        public IStatusGeneric Update(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();
            var post = Db.Posts.Include(p => p.Tags).SingleOrDefault(p => p.PostId == dto.PostId);
            if (post == null)
                return PostNotFound(status);

            var tags = CheckSelections(dto, status);
            if (!status.IsValid)
                return status;

            CopyDtoToPost(dto, post);
            ReplaceTags(post, tags);
            return SaveAndSetMessage(status, "Successfully updated the Post", null);
        }

        //---------------------------------------------------
        //private helpers

        private DetailPostDto ReadAndSetup(int postId)
        {
            var dto = _crudServices.ReadSingle<DetailPostDto>(postId);
            var status = new StatusGenericHandler();
            Status = status;
            if (dto == null)
            {
                PostNotFound(status);
                return null;
            }
            SetupSecondaryData(dto, BlogsForList().ToList(), TagsForList().ToList(),
                SelectedBlogValueForExisting(dto), SelectedTagIdsForExisting(dto));
            return dto;
        }

        private List<Tag> CheckSelections(DetailPostDto dto, StatusGenericHandler status)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            var bloggerExists = blogId != null && Db.Blogs.Any(x => x.BlogId == blogId);
            var tagIds = GetUserTagIds(dto);
            var foundTags = Db.Tags.Where(x => tagIds.Contains(x.TagId)).ToList();
            return SetupRestOfDto(dto, status, bloggerExists, foundTags, tagIds);
        }

        private IStatusGeneric SaveAndSetMessage(StatusGenericHandler status, string successMessage, Action onSuccess)
        {
            status.CombineStatuses(Db.SaveChangesWithChecking());
            if (status.IsValid)
            {
                onSuccess?.Invoke();
                status.Message = successMessage;
            }
            else
                Db.ChangeTracker.Clear();
            return status;
        }
    }
}

using System.Linq;
using AutoMapper;
using AutoMapper.QueryableExtensions;
using GenericLibsBase.Core;
using GenericServices;
using GenericServices.Core;
using GenericServices.Internal;

namespace GenericServices.Services
{
    public class ListService : GenericServiceBase, IListService
    {
        public ListService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public IQueryable<T> GetAll<T>() where T : class
        {
            if (!IsDto(typeof(T)))
                return Context.Set<T>();

            var pair = DtoReflection.GetEntityAndDtoTypes(typeof(T));
            var source = DtoReflection.QueryableForEntity(Context, pair.Item1);
            return source.ProjectTo<T>(Mapper.ConfigurationProvider);
        }
    }

    public class DetailService : GenericServiceBase, IDetailService
    {
        public DetailService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public ISuccessOrErrors<T> GetDetail<T>(params object[] keys) where T : class
        {
            return SyncReadHelper.ReadSingle<T>(Context, Mapper, keys);
        }
    }

    public class UpdateSetupService : GenericServiceBase, IUpdateSetupService
    {
        public UpdateSetupService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public ISuccessOrErrors<T> GetOriginal<T>(params object[] keys) where T : class
        {
            return SyncReadHelper.ReadSingle<T>(Context, Mapper, keys);
        }
    }

    public class CreateSetupService : GenericServiceBase, ICreateSetupService
    {
        public CreateSetupService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public T GetDto<T>() where T : class
        {
            var dto = System.Activator.CreateInstance<T>();
            var crud = dto as ISyncInternalDtoCrud;
            if (crud != null)
                crud.RunSetupSecondaryData(Context);
            return dto;
        }
    }

    public class CreateService : GenericServiceBase, ICreateService
    {
        public CreateService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public ISuccessOrErrors Create<T>(T item) where T : class
        {
            var crud = item as ISyncInternalDtoCrud;
            if (crud != null)
            {
                var buildStatus = crud.RunCreateDataFromDto(Context);
                if (!buildStatus.IsValid)
                {
                    crud.RunSetupSecondaryData(Context);
                    return buildStatus;
                }

                var saveStatus = Context.SaveChangesWithChecking();
                if (saveStatus.IsValid)
                    saveStatus.SetSuccessMessage("Successfully created {0}.", crud.AssociatedEntityType.Name);
                else
                    crud.RunSetupSecondaryData(Context);
                return saveStatus;
            }

            Context.Set<T>().Add(item);
            var status = Context.SaveChangesWithChecking();
            if (status.IsValid)
                status.SetSuccessMessage("Successfully created {0}.", typeof(T).Name);
            return status;
        }

        public T ResetDto<T>(T dto) where T : class
        {
            var crud = dto as ISyncInternalDtoCrud;
            if (crud != null)
                crud.RunSetupSecondaryData(Context);
            return dto;
        }
    }

    public class UpdateService : GenericServiceBase, IUpdateService
    {
        public UpdateService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public ISuccessOrErrors Update<T>(T item) where T : class
        {
            var crud = item as ISyncInternalDtoCrud;
            if (crud != null)
            {
                var entity = DtoReflection.FindEntity(Context, crud.AssociatedEntityType, crud.GetKeyValues());
                if (entity == null)
                {
                    crud.RunSetupSecondaryData(Context);
                    return new SuccessOrErrors().AddSingleError(
                        "Could not find the item you requested. Perhaps it was deleted by another user.");
                }

                var buildStatus = crud.RunUpdateDataFromDto(Context, entity);
                if (!buildStatus.IsValid)
                {
                    crud.RunSetupSecondaryData(Context);
                    return buildStatus;
                }

                var saveStatus = Context.SaveChangesWithChecking();
                if (saveStatus.IsValid)
                    saveStatus.SetSuccessMessage("Successfully updated {0}.", crud.AssociatedEntityType.Name);
                else
                    crud.RunSetupSecondaryData(Context);
                return saveStatus;
            }

            Context.Entry(item).State = Microsoft.EntityFrameworkCore.EntityState.Modified;
            var status = Context.SaveChangesWithChecking();
            if (status.IsValid)
                status.SetSuccessMessage("Successfully updated {0}.", typeof(T).Name);
            return status;
        }

        public T ResetDto<T>(T dto) where T : class
        {
            var crud = dto as ISyncInternalDtoCrud;
            if (crud != null)
                crud.RunSetupSecondaryData(Context);
            return dto;
        }
    }

    public class DeleteService : GenericServiceBase, IDeleteService
    {
        public DeleteService(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public ISuccessOrErrors Delete<T>(params object[] keys) where T : class
        {
            var entity = Context.Set<T>().Find(keys);
            if (entity == null)
                return new SuccessOrErrors().AddSingleError(
                    "Could not find the {0} you requested. Perhaps it was deleted by another user.", typeof(T).Name);

            Context.Set<T>().Remove(entity);
            var status = Context.SaveChangesWithChecking();
            if (status.IsValid)
                status.SetSuccessMessage("Successfully deleted {0}.", typeof(T).Name);
            return status;
        }
    }

    internal static class SyncReadHelper
    {
        public static ISuccessOrErrors<T> ReadSingle<T>(IGenericServicesDbContext context, IMapper mapper, object[] keys) where T : class
        {
            if (!DtoReflection.IsDto(typeof(T)))
            {
                var entity = context.Set<T>().Find(keys);
                if (entity == null)
                    return new SuccessOrErrors<T>().AddSingleError("Could not find the data entry you requested.") as ISuccessOrErrors<T>;
                return new SuccessOrErrors<T>().SetSuccessWithResult(entity, "Success");
            }

            var pair = DtoReflection.GetEntityAndDtoTypes(typeof(T));
            var source = DtoReflection.QueryableForEntity(context, pair.Item1);
            var projected = source.ProjectTo<T>(mapper.ConfigurationProvider);
            var predicate = GenericServiceBase.BuildKeyPredicate<T>(keys);
            var dto = projected.Where(predicate).SingleOrDefault();

            if (dto == null)
                return new SuccessOrErrors<T>().AddSingleError("Could not find the data entry you requested.") as ISuccessOrErrors<T>;

            var crud = dto as ISyncInternalDtoCrud;
            if (crud != null)
                crud.RunSetupSecondaryData(context);

            return new SuccessOrErrors<T>().SetSuccessWithResult(dto, "Success");
        }
    }
}

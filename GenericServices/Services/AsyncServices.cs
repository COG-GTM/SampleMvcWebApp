using System.Linq;
using System.Threading.Tasks;
using AutoMapper;
using AutoMapper.QueryableExtensions;
using GenericLibsBase.Core;
using GenericServices;
using GenericServices.Core;
using GenericServices.Internal;
using Microsoft.EntityFrameworkCore;

namespace GenericServices.Services
{
    public class DetailServiceAsync : GenericServiceBase, IDetailServiceAsync
    {
        public DetailServiceAsync(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public Task<ISuccessOrErrors<T>> GetDetailAsync<T>(params object[] keys) where T : class
        {
            return AsyncReadHelper.ReadSingleAsync<T>(Context, Mapper, keys);
        }
    }

    public class UpdateSetupServiceAsync : GenericServiceBase, IUpdateSetupServiceAsync
    {
        public UpdateSetupServiceAsync(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public Task<ISuccessOrErrors<T>> GetOriginalAsync<T>(params object[] keys) where T : class
        {
            return AsyncReadHelper.ReadSingleAsync<T>(Context, Mapper, keys);
        }
    }

    public class CreateSetupServiceAsync : GenericServiceBase, ICreateSetupServiceAsync
    {
        public CreateSetupServiceAsync(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public async Task<T> GetDtoAsync<T>() where T : class
        {
            var dto = System.Activator.CreateInstance<T>();
            var crud = dto as IAsyncInternalDtoCrud;
            if (crud != null)
                await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
            return dto;
        }
    }

    public class CreateServiceAsync : GenericServiceBase, ICreateServiceAsync
    {
        public CreateServiceAsync(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public async Task<ISuccessOrErrors> CreateAsync<T>(T item) where T : class
        {
            var crud = item as IAsyncInternalDtoCrud;
            if (crud != null)
            {
                var buildStatus = await crud.RunCreateDataFromDtoAsync(Context).ConfigureAwait(false);
                if (!buildStatus.IsValid)
                {
                    await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
                    return buildStatus;
                }

                var saveStatus = await Context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
                if (saveStatus.IsValid)
                    saveStatus.SetSuccessMessage("Successfully created {0}.", crud.AssociatedEntityType.Name);
                else
                    await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
                return saveStatus;
            }

            Context.Set<T>().Add(item);
            var status = await Context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
            if (status.IsValid)
                status.SetSuccessMessage("Successfully created {0}.", typeof(T).Name);
            return status;
        }

        public async Task<T> ResetDtoAsync<T>(T dto) where T : class
        {
            var crud = dto as IAsyncInternalDtoCrud;
            if (crud != null)
                await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
            return dto;
        }
    }

    public class UpdateServiceAsync : GenericServiceBase, IUpdateServiceAsync
    {
        public UpdateServiceAsync(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public async Task<ISuccessOrErrors> UpdateAsync<T>(T item) where T : class
        {
            var crud = item as IAsyncInternalDtoCrud;
            if (crud != null)
            {
                var entity = await DtoReflection.FindEntityAsync(Context, crud.AssociatedEntityType, crud.GetKeyValues()).ConfigureAwait(false);
                if (entity == null)
                {
                    await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
                    return new SuccessOrErrors().AddSingleError(
                        "Could not find the item you requested. Perhaps it was deleted by another user.");
                }

                var buildStatus = await crud.RunUpdateDataFromDtoAsync(Context, entity).ConfigureAwait(false);
                if (!buildStatus.IsValid)
                {
                    await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
                    return buildStatus;
                }

                var saveStatus = await Context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
                if (saveStatus.IsValid)
                    saveStatus.SetSuccessMessage("Successfully updated {0}.", crud.AssociatedEntityType.Name);
                else
                    await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
                return saveStatus;
            }

            Context.Entry(item).State = EntityState.Modified;
            var status = await Context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
            if (status.IsValid)
                status.SetSuccessMessage("Successfully updated {0}.", typeof(T).Name);
            return status;
        }

        public async Task<T> ResetDtoAsync<T>(T dto) where T : class
        {
            var crud = dto as IAsyncInternalDtoCrud;
            if (crud != null)
                await crud.RunSetupSecondaryDataAsync(Context).ConfigureAwait(false);
            return dto;
        }
    }

    public class DeleteServiceAsync : GenericServiceBase, IDeleteServiceAsync
    {
        public DeleteServiceAsync(IGenericServicesDbContext context, IMapper mapper) : base(context, mapper) { }

        public async Task<ISuccessOrErrors> DeleteAsync<T>(params object[] keys) where T : class
        {
            var entity = await Context.Set<T>().FindAsync(keys).AsTask().ConfigureAwait(false);
            if (entity == null)
                return new SuccessOrErrors().AddSingleError(
                    "Could not find the {0} you requested. Perhaps it was deleted by another user.", typeof(T).Name);

            Context.Set<T>().Remove(entity);
            var status = await Context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
            if (status.IsValid)
                status.SetSuccessMessage("Successfully deleted {0}.", typeof(T).Name);
            return status;
        }
    }

    internal static class AsyncReadHelper
    {
        public static async Task<ISuccessOrErrors<T>> ReadSingleAsync<T>(IGenericServicesDbContext context, IMapper mapper, object[] keys) where T : class
        {
            if (!DtoReflection.IsDto(typeof(T)))
            {
                var entity = await context.Set<T>().FindAsync(keys).AsTask().ConfigureAwait(false);
                if (entity == null)
                    return (ISuccessOrErrors<T>)new SuccessOrErrors<T>().AddSingleError("Could not find the data entry you requested.");
                return new SuccessOrErrors<T>().SetSuccessWithResult(entity, "Success");
            }

            var pair = DtoReflection.GetEntityAndDtoTypes(typeof(T));
            var source = DtoReflection.QueryableForEntity(context, pair.Item1);
            var projected = source.ProjectTo<T>(mapper.ConfigurationProvider);
            var predicate = GenericServiceBase.BuildKeyPredicate<T>(keys);
            var dto = await projected.Where(predicate).SingleOrDefaultAsync().ConfigureAwait(false);

            if (dto == null)
                return (ISuccessOrErrors<T>)new SuccessOrErrors<T>().AddSingleError("Could not find the data entry you requested.");

            var crud = dto as IAsyncInternalDtoCrud;
            if (crud != null)
                await crud.RunSetupSecondaryDataAsync(context).ConfigureAwait(false);

            return new SuccessOrErrors<T>().SetSuccessWithResult(dto, "Success");
        }
    }
}

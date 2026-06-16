using System.Linq;
using System.Threading.Tasks;
using AutoMapper;
using GenericLibsBase.Core;
using GenericServices.Core;
using Microsoft.EntityFrameworkCore;

namespace GenericServices
{
    /// <summary>
    /// Shared helpers for the concrete service implementations.
    /// </summary>
    internal static class ServiceHelpers
    {
        public static bool IsDto<T>()
        {
            return typeof(EfGenericDtoBase).IsAssignableFrom(typeof(T));
        }
    }

    public class ListService : IListService
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public ListService(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public IQueryable<TDto> GetAll<TDto>() where TDto : EfGenericDtoBase, new()
        {
            var dto = new TDto();
            return (IQueryable<TDto>)dto.BuildListQuery(_context, _mapper);
        }
    }

    public class DetailService : IDetailService
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public DetailService(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public ISuccessOrErrors<T> GetDetail<T>(int id) where T : class, new()
        {
            if (ServiceHelpers.IsDto<T>())
            {
                var dto = (EfGenericDtoBase)(object)new T();
                var result = (T)dto.GetDetailDto(_context, _mapper, id);
                return WrapResult(result);
            }

            var entity = _context.Set<T>().Find(id);
            return WrapResult(entity);
        }

        private static ISuccessOrErrors<T> WrapResult<T>(T result) where T : class
        {
            if (result == null)
                return (ISuccessOrErrors<T>)new SuccessOrErrors<T>()
                    .AddSingleError("We could not find the item you requested. Did another user delete it?");
            return new SuccessOrErrors<T>().SetSuccessWithResult(result, "Success");
        }
    }

    public class DetailServiceAsync : IDetailServiceAsync
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public DetailServiceAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<ISuccessOrErrors<T>> GetDetailAsync<T>(int id) where T : class, new()
        {
            T result;
            if (ServiceHelpers.IsDto<T>())
            {
                var dto = (EfGenericDtoBase)(object)new T();
                result = (T)await dto.GetDetailDtoAsync(_context, _mapper, id).ConfigureAwait(false);
            }
            else
            {
                result = await _context.Set<T>().FindAsync(id).ConfigureAwait(false);
            }

            if (result == null)
                return (ISuccessOrErrors<T>)new SuccessOrErrors<T>()
                    .AddSingleError("We could not find the item you requested. Did another user delete it?");
            return new SuccessOrErrors<T>().SetSuccessWithResult(result, "Success");
        }
    }

    public class CreateSetupService : ICreateSetupService
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public CreateSetupService(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public TDto GetDto<TDto>() where TDto : EfGenericDtoBase, new()
        {
            var dto = new TDto();
            dto.RunSetupSecondaryData(_context, _mapper);
            return dto;
        }
    }

    public class CreateSetupServiceAsync : ICreateSetupServiceAsync
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public CreateSetupServiceAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<TDto> GetDtoAsync<TDto>() where TDto : EfGenericDtoBase, new()
        {
            var dto = new TDto();
            await dto.RunSetupSecondaryDataAsync(_context, _mapper).ConfigureAwait(false);
            return dto;
        }
    }

    public class CreateService : ICreateService
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public CreateService(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public ISuccessOrErrors Create<T>(T item) where T : class
        {
            if (item is EfGenericDtoBase dto)
                return dto.CreateInDb(_context, _mapper);

            _context.Set<T>().Add(item);
            return _context.SaveChangesWithChecking();
        }

        public T ResetDto<T>(T dto) where T : class
        {
            if (dto is EfGenericDtoBase baseDto)
                baseDto.RunSetupSecondaryData(_context, _mapper);
            return dto;
        }
    }

    public class CreateServiceAsync : ICreateServiceAsync
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public CreateServiceAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<ISuccessOrErrors> CreateAsync<T>(T item) where T : class
        {
            if (item is EfGenericDtoBase dto)
                return await dto.CreateInDbAsync(_context, _mapper).ConfigureAwait(false);

            _context.Set<T>().Add(item);
            return await _context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
        }

        public async Task<T> ResetDtoAsync<T>(T dto) where T : class
        {
            if (dto is EfGenericDtoBase baseDto)
                await baseDto.RunSetupSecondaryDataAsync(_context, _mapper).ConfigureAwait(false);
            return dto;
        }
    }

    public class UpdateSetupService : IUpdateSetupService
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public UpdateSetupService(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public ISuccessOrErrors<T> GetOriginal<T>(int id) where T : class, new()
        {
            if (ServiceHelpers.IsDto<T>())
            {
                var dto = (EfGenericDtoBase)(object)new T();
                var result = (T)dto.GetDetailDto(_context, _mapper, id);
                return Wrap(result);
            }

            var entity = _context.Set<T>().Find(id);
            return Wrap(entity);
        }

        private static ISuccessOrErrors<T> Wrap<T>(T result) where T : class
        {
            if (result == null)
                return (ISuccessOrErrors<T>)new SuccessOrErrors<T>()
                    .AddSingleError("We could not find the item you requested. Did another user delete it?");
            return new SuccessOrErrors<T>().SetSuccessWithResult(result, "Success");
        }
    }

    public class UpdateSetupServiceAsync : IUpdateSetupServiceAsync
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public UpdateSetupServiceAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<ISuccessOrErrors<T>> GetOriginalAsync<T>(int id) where T : class, new()
        {
            T result;
            if (ServiceHelpers.IsDto<T>())
            {
                var dto = (EfGenericDtoBase)(object)new T();
                result = (T)await dto.GetDetailDtoAsync(_context, _mapper, id).ConfigureAwait(false);
            }
            else
            {
                result = await _context.Set<T>().FindAsync(id).ConfigureAwait(false);
            }

            if (result == null)
                return (ISuccessOrErrors<T>)new SuccessOrErrors<T>()
                    .AddSingleError("We could not find the item you requested. Did another user delete it?");
            return new SuccessOrErrors<T>().SetSuccessWithResult(result, "Success");
        }
    }

    public class UpdateService : IUpdateService
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public UpdateService(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public ISuccessOrErrors Update<T>(T item) where T : class
        {
            if (item is EfGenericDtoBase dto)
                return dto.UpdateInDb(_context, _mapper);

            _context.Set<T>().Update(item);
            return _context.SaveChangesWithChecking();
        }

        public T ResetDto<T>(T dto) where T : class
        {
            if (dto is EfGenericDtoBase baseDto)
                baseDto.RunSetupSecondaryData(_context, _mapper);
            return dto;
        }
    }

    public class UpdateServiceAsync : IUpdateServiceAsync
    {
        private readonly IGenericServicesDbContext _context;
        private readonly IMapper _mapper;

        public UpdateServiceAsync(IGenericServicesDbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<ISuccessOrErrors> UpdateAsync<T>(T item) where T : class
        {
            if (item is EfGenericDtoBase dto)
                return await dto.UpdateInDbAsync(_context, _mapper).ConfigureAwait(false);

            _context.Set<T>().Update(item);
            return await _context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
        }

        public async Task<T> ResetDtoAsync<T>(T dto) where T : class
        {
            if (dto is EfGenericDtoBase baseDto)
                await baseDto.RunSetupSecondaryDataAsync(_context, _mapper).ConfigureAwait(false);
            return dto;
        }
    }

    public class DeleteService : IDeleteService
    {
        private readonly IGenericServicesDbContext _context;

        public DeleteService(IGenericServicesDbContext context)
        {
            _context = context;
        }

        public ISuccessOrErrors Delete<T>(int id) where T : class
        {
            var entity = _context.Set<T>().Find(id);
            if (entity == null)
                return new SuccessOrErrors().AddSingleError(
                    "Could not delete the {0}. Did another user delete it already?", typeof(T).Name);
            _context.Set<T>().Remove(entity);
            return _context.SaveChangesWithChecking();
        }
    }

    public class DeleteServiceAsync : IDeleteServiceAsync
    {
        private readonly IGenericServicesDbContext _context;

        public DeleteServiceAsync(IGenericServicesDbContext context)
        {
            _context = context;
        }

        public async Task<ISuccessOrErrors> DeleteAsync<T>(int id) where T : class
        {
            var entity = await _context.Set<T>().FindAsync(id).ConfigureAwait(false);
            if (entity == null)
                return new SuccessOrErrors().AddSingleError(
                    "Could not delete the {0}. Did another user delete it already?", typeof(T).Name);
            _context.Set<T>().Remove(entity);
            return await _context.SaveChangesWithCheckingAsync().ConfigureAwait(false);
        }
    }
}

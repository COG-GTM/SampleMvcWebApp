using System.ComponentModel.DataAnnotations;

namespace SampleWebApp.Core.Common
{
    public abstract class BaseDto
    {
        [Key]
        public int Id { get; set; }
    }

    public abstract class BaseDto<TEntity> : BaseDto where TEntity : class
    {
    }
}

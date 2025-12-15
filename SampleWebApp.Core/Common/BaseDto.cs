using System;

namespace SampleWebApp.Core.Common
{
    public abstract class BaseDto
    {
        public int Id { get; set; }
    }

    public abstract class BaseDto<TEntity> : BaseDto where TEntity : class
    {
    }
}

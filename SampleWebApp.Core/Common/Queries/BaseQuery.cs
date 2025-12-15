using MediatR;

namespace SampleWebApp.Core.Common.Queries
{
    public abstract class BaseQuery<TResponse> : IRequest<TResponse> where TResponse : class
    {
    }
}

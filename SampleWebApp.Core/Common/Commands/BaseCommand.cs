using MediatR;

namespace SampleWebApp.Core.Common.Commands
{
    public abstract class BaseCommand<TResponse> : IRequest<TResponse> where TResponse : class
    {
    }
}

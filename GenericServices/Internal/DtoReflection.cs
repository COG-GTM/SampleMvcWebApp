using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Threading.Tasks;
using GenericServices.Core;
using Microsoft.EntityFrameworkCore;

namespace GenericServices.Internal
{
    /// <summary>
    /// Reflection helpers shared by the mapper configuration and the service layer.
    /// </summary>
    internal static class DtoReflection
    {
        public static bool IsDto(Type type)
        {
            return typeof(EfGenericDtoBase).IsAssignableFrom(type);
        }

        /// <summary>
        /// Walks the base chain to find <see cref="EfGenericDtoBase{TEntity,TDto}"/> and returns
        /// (entityType, dtoType), or null if the type is not a DTO.
        /// </summary>
        public static Tuple<Type, Type> GetEntityAndDtoTypes(Type dtoType)
        {
            var t = dtoType;
            while (t != null && t != typeof(object))
            {
                if (t.IsGenericType && t.GetGenericTypeDefinition() == typeof(EfGenericDtoBase<,>))
                {
                    var args = t.GetGenericArguments();
                    return Tuple.Create(args[0], args[1]);
                }
                t = t.BaseType;
            }
            return null;
        }

        /// <summary>
        /// Returns true if a source member on <paramref name="entityType"/> can supply
        /// <paramref name="memberName"/> either directly or by AutoMapper flattening
        /// (e.g. "BloggerName" -> Blogger.Name, "PostsCount" -> Posts.Count).
        /// </summary>
        public static bool CanResolveSource(Type entityType, string memberName)
        {
            if (string.IsNullOrEmpty(memberName)) return false;

            if (GetPublicProperty(entityType, memberName) != null)
                return true;

            //greedy longest-prefix flattening
            for (var i = memberName.Length - 1; i >= 1; i--)
            {
                var prefix = memberName.Substring(0, i);
                var remainder = memberName.Substring(i);
                var prop = GetPublicProperty(entityType, prefix);
                if (prop == null) continue;

                if (IsEnumerableButNotString(prop.PropertyType))
                {
                    if (remainder == "Count")
                        return true;
                    continue;
                }

                if (CanResolveSource(prop.PropertyType, remainder))
                    return true;
            }

            return false;
        }

        private static PropertyInfo GetPublicProperty(Type type, string name)
        {
            return type.GetProperty(name, BindingFlags.Public | BindingFlags.Instance | BindingFlags.IgnoreCase);
        }

        public static bool IsEnumerableButNotString(Type type)
        {
            return type != typeof(string) && typeof(IEnumerable).IsAssignableFrom(type);
        }

        //--------------------------------------------------
        //runtime access to Set<TEntity>() / Find for a type only known at runtime

        private static readonly MethodInfo SetMethod = typeof(IGenericServicesDbContext)
            .GetMethod("Set", Type.EmptyTypes);

        public static IQueryable QueryableForEntity(IGenericServicesDbContext context, Type entityType)
        {
            var generic = SetMethod.MakeGenericMethod(entityType);
            return (IQueryable)generic.Invoke(context, null);
        }

        public static object FindEntity(IGenericServicesDbContext context, Type entityType, object[] keys)
        {
            var set = SetMethod.MakeGenericMethod(entityType).Invoke(context, null);
            var find = set.GetType().GetMethod("Find", new[] { typeof(object[]) });
            return find.Invoke(set, new object[] { keys });
        }

        public static async Task<object> FindEntityAsync(IGenericServicesDbContext context, Type entityType, object[] keys)
        {
            var set = SetMethod.MakeGenericMethod(entityType).Invoke(context, null);
            var find = set.GetType().GetMethod("FindAsync", new[] { typeof(object[]) });
            var valueTask = find.Invoke(set, new object[] { keys });

            // DbSet.FindAsync returns ValueTask<TEntity>; convert to Task<object> via AsTask()
            var asTask = valueTask.GetType().GetMethod("AsTask").Invoke(valueTask, null);
            await (Task)asTask;
            var resultProp = asTask.GetType().GetProperty("Result");
            return resultProp.GetValue(asTask);
        }
    }
}

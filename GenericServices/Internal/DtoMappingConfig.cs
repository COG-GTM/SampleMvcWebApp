using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using AutoMapper;

namespace GenericServices.Internal
{
    /// <summary>
    /// Builds the AutoMapper read maps used to project entities onto DTOs (entity -> DTO),
    /// relying on AutoMapper flattening for members like BloggerName / PostsCount.
    /// Writing DTOs back onto entities is handled by <see cref="Core.EntityFromDto"/>, not
    /// AutoMapper, so no DTO -> entity maps are configured here.
    /// </summary>
    internal static class DtoMappingConfig
    {
        public static void AddMappings(IMapperConfigurationExpression cfg, IEnumerable<Type> dtoTypes)
        {
            var entityMemberTypes = new HashSet<Type>();

            foreach (var dtoType in dtoTypes)
            {
                var pair = DtoReflection.GetEntityAndDtoTypes(dtoType);
                if (pair == null) continue;

                var entityType = pair.Item1;
                BuildReadMap(cfg, entityType, dtoType, entityMemberTypes);
            }

            //DTOs sometimes expose the entity type directly (e.g. ICollection<Tag> Tags). Add a
            //self-map for those types that copies only scalar members, so projection works and
            //we never recurse through their navigations.
            foreach (var entityMemberType in entityMemberTypes)
                BuildScalarSelfMap(cfg, entityMemberType);
        }

        private static void BuildReadMap(IMapperConfigurationExpression cfg, Type entityType, Type dtoType,
            HashSet<Type> entityMemberTypes)
        {
            var map = cfg.CreateMap(entityType, dtoType);

            foreach (var prop in PublicProps(dtoType))
            {
                //computed (read-only) properties are evaluated in memory, never projected
                if (!prop.CanWrite)
                {
                    map.ForMember(prop.Name, o => o.Ignore());
                    continue;
                }

                //members with no matching source (e.g. UI dropdown lists) are filled elsewhere
                if (!DtoReflection.CanResolveSource(entityType, prop.Name))
                {
                    map.ForMember(prop.Name, o => o.Ignore());
                    continue;
                }

                var memberEntityType = EntityElementType(prop.PropertyType);
                if (memberEntityType != null)
                    entityMemberTypes.Add(memberEntityType);
            }
        }

        private static void BuildScalarSelfMap(IMapperConfigurationExpression cfg, Type type)
        {
            var map = cfg.CreateMap(type, type);
            foreach (var prop in PublicProps(type))
            {
                if (DtoReflection.IsEnumerableButNotString(prop.PropertyType) || IsEntityLike(prop.PropertyType))
                    map.ForMember(prop.Name, o => o.Ignore());
            }
        }

        /// <summary>
        /// If the property is an entity or a collection of entities, returns that entity type;
        /// otherwise null.
        /// </summary>
        private static Type EntityElementType(Type propertyType)
        {
            if (DtoReflection.IsEnumerableButNotString(propertyType))
            {
                var element = GetEnumerableElementType(propertyType);
                return element != null && IsEntityLike(element) ? element : null;
            }

            return IsEntityLike(propertyType) ? propertyType : null;
        }

        private static Type GetEnumerableElementType(Type type)
        {
            if (type.IsArray)
                return type.GetElementType();
            if (type.IsGenericType)
                return type.GetGenericArguments().FirstOrDefault();
            var iface = type.GetInterfaces()
                .FirstOrDefault(i => i.IsGenericType && i.GetGenericTypeDefinition() == typeof(IEnumerable<>));
            return iface != null ? iface.GetGenericArguments()[0] : null;
        }

        private static bool IsEntityLike(Type type)
        {
            return type.IsClass
                   && type != typeof(string)
                   && (type.Namespace == null || !type.Namespace.StartsWith("System"));
        }

        private static IEnumerable<PropertyInfo> PublicProps(Type type)
        {
            return type.GetProperties(BindingFlags.Public | BindingFlags.Instance)
                .Where(p => p.GetIndexParameters().Length == 0);
        }
    }
}

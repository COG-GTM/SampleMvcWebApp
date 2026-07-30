#region licence
// The MIT License (MIT)
// 
// Filename: PostDtoConfigs.cs
// Date Created: 2014/05/20
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System;
using AutoMapper;
using DataLayer.DataClasses.Concrete;
using GenericServices.Configuration;

namespace ServiceLayer.PostServices
{
    //EfCore.GenericServices maps a ILinkToEntity DTO both ways. These configurations stop the
    //properties that the DTO only uses for display, or that IPostDtoService looks after itself,
    //from being copied back into the Post entity.

    public class SimplePostDtoConfig : PerDtoConfig<SimplePostDto, Post>
    {
        public override Action<IMappingExpression<SimplePostDto, Post>> AlterSaveMapping
        {
            get { return cfg => cfg.ForMember(x => x.Tags, opt => opt.Ignore()); }
        }
    }

    public class SimplePostDtoAsyncConfig : PerDtoConfig<SimplePostDtoAsync, Post>
    {
        public override Action<IMappingExpression<SimplePostDtoAsync, Post>> AlterSaveMapping
        {
            get { return cfg => cfg.ForMember(x => x.Tags, opt => opt.Ignore()); }
        }
    }

    public class DetailPostDtoConfig : PerDtoConfig<DetailPostDto, Post>
    {
        public override Action<IMappingExpression<DetailPostDto, Post>> AlterSaveMapping
        {
            get { return cfg => cfg.ForMember(x => x.Tags, opt => opt.Ignore()); }
        }
    }

    public class DetailPostDtoAsyncConfig : PerDtoConfig<DetailPostDtoAsync, Post>
    {
        public override Action<IMappingExpression<DetailPostDtoAsync, Post>> AlterSaveMapping
        {
            get { return cfg => cfg.ForMember(x => x.Tags, opt => opt.Ignore()); }
        }
    }
}

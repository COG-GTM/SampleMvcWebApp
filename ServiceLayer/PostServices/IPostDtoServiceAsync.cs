#region licence
// The MIT License (MIT)
// 
// Filename: IPostDtoServiceAsync.cs
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
using System.Threading.Tasks;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// The async version of IPostDtoService, which works on DetailPostDtoAsync
    /// </summary>
    public interface IPostDtoServiceAsync
    {
        /// <summary>
        /// This returns an empty DTO with the blogger and tag lists filled in, ready for a create
        /// </summary>
        Task<DetailPostDtoAsync> GetDtoForCreateAsync();

        /// <summary>
        /// This returns the DTO of the given post with the blogger and tag lists filled in,
        /// or null if the post was not found
        /// </summary>
        Task<DetailPostDtoAsync> GetDtoForUpdateAsync(int postId);

        /// <summary>
        /// This refills the blogger and tag lists after a failed create/update, so the DTO can be
        /// shown again. It replaces the old IUpdateServiceAsync.ResetDtoAsync
        /// </summary>
        Task ResetSecondaryDataAsync(DetailPostDtoAsync dto);

        Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto);

        Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto);
    }
}

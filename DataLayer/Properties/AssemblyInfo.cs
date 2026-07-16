using System.Runtime.CompilerServices;

//The <InternalsVisibleTo> MSBuild item is only emitted when GenerateAssemblyInfo is true, but this
//project sets GenerateAssemblyInfo=false, so the attribute is declared explicitly here instead.
[assembly: InternalsVisibleTo("Tests")]

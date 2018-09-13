package ParserExamples.Services;import System.ServiceModel;import System.ServiceModel.Web;import ParserExamples.Models;import ParserExamples.Searching;/// <summary>/// This interface provides the contract for track searching./// </summary>/// <remarks>/// Implementations are expected to be effectively thread-safe./// </remarks>[ServiceContract]public interface ITrackSearchService{	/// <summary>	/// Searches tracks.	/// </summary>	/// <param name="criteria">The search criteria</param>	/// <returns>The search result</returns>	@OperationContract	@WebInvoke(Method = "POST", UriTemplate = "/TrackSearch",		RequestFormat = WebMessageFormat.Json, ResponseFormat = WebMessageFormat.Json)	@TransactionFlow(TransactionFlowOption.Allowed)	SearchResult<TrackInfo<String>, AlbumInfo<String>> Search(TrackSearchCriteria criteria) { ; }	/// <summary>	/// Searches tracks that have past due date.	/// </summary>	/// <param name="albumName">The album name</param>	/// <returns>The search result</returns>	@OperationContract	@WebInvoke(Method = "POST", UriTemplate = "/GetAlbumTracks?albumName={albumName}",		ResponseFormat = WebMessageFormat.Json)	@TransactionFlow(TransactionFlowOption.Allowed)	SearchResult<Dictionary<AlbumInfo, List<TrackInfo>>> GetAlbumTracks(String albumName) {		content of block;	}}
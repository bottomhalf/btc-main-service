package bt.conference.dto;

import lombok.Data;

import java.util.List;

@Data
public class PagedResponse<T> {
    private List<T> data;
    private long totalRecords;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> PagedResponse<T> of(
            List<T> data,
            long totalRecords,
            int currentPage,
            int pageSize
    ) {
        PagedResponse<T> response = new PagedResponse<>();
        response.setData(data);
        response.setTotalRecords(totalRecords);
        response.setCurrentPage(currentPage);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) totalRecords / pageSize));
        response.setHasNext(currentPage < response.getTotalPages());
        response.setHasPrevious(currentPage > 1);
        return response;
    }
}

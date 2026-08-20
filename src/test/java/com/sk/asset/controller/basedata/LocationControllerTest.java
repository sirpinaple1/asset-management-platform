package com.sk.asset.controller.basedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.asset.common.GlobalExceptionHandler;
import com.sk.asset.dto.basedata.location.LocationReq;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.service.basedata.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocationControllerTest {

    private final LocationService locationService = mock(LocationService.class);
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    LocationControllerTest() throws Exception {
        LocationController controller = new LocationController();
        Field field = LocationController.class.getDeclaredField("locationService");
        field.setAccessible(true);
        field.set(controller, locationService);

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldReturn200WithFlatList() throws Exception {
        Location l1 = new Location();
        l1.setId(1L);
        l1.setName("森科");
        l1.setPath("/1/");

        Location l2 = new Location();
        l2.setId(2L);
        l2.setName("物料仓");
        l2.setParentId(1L);
        l2.setPath("/1/2/");

        when(locationService.list()).thenReturn(Arrays.asList(l1, l2));

        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("森科"))
                .andExpect(jsonPath("$.data[0].path").value("/1/"))
                .andExpect(jsonPath("$.data[1].parentId").value(1));
    }

    @Test
    void list_shouldReturnChildrenWhenParentIdProvided() throws Exception {
        Location child = new Location();
        child.setId(2L);
        child.setName("物料仓");
        child.setParentId(1L);

        when(locationService.listChildren(1L)).thenReturn(List.of(child));

        mockMvc.perform(get("/api/v1/locations?parentId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("物料仓"));

        verify(locationService, times(1)).listChildren(1L);
        verify(locationService, never()).list();
    }

    @Test
    void getById_shouldReturn200WhenExists() throws Exception {
        Location loc = new Location();
        loc.setId(1L);
        loc.setName("森科");

        when(locationService.getById(1L)).thenReturn(loc);

        mockMvc.perform(get("/api/v1/locations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("森科"));
    }

    @Test
    void getById_shouldReturn404WhenNotFound() throws Exception {
        when(locationService.getById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/locations/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void create_shouldReturn200AndCallService() throws Exception {
        LocationReq req = new LocationReq();
        req.setName("测试位置");
        req.setCode("TEST");
        req.setPath("/1/");

        doAnswer(invocation -> {
            Location arg = invocation.getArgument(0);
            arg.setId(1L);
            return null;
        }).when(locationService).save(any(Location.class));

        mockMvc.perform(post("/api/v1/locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(locationService, times(1)).save(any(Location.class));
    }

    @Test
    void update_shouldReturn200WhenExists() throws Exception {
        LocationReq req = new LocationReq();
        req.setName("更新位置");

        Location existing = new Location();
        existing.setId(1L);
        existing.setName("旧位置");

        when(locationService.getById(1L)).thenReturn(existing);

        mockMvc.perform(put("/api/v1/locations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(locationService, times(1)).updateById(any(Location.class));
    }

    @Test
    void update_shouldReturn404WhenNotFound() throws Exception {
        LocationReq req = new LocationReq();
        req.setName("更新位置");

        when(locationService.getById(99L)).thenReturn(null);

        mockMvc.perform(put("/api/v1/locations/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/v1/locations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(locationService, times(1)).deleteById(1L);
    }
}

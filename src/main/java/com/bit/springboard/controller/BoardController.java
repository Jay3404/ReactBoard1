package com.bit.springboard.controller;

import com.bit.springboard.common.FileUtils;
import com.bit.springboard.dto.BoardDTO;
import com.bit.springboard.dto.BoardFileDTO;
import com.bit.springboard.dto.ResponseDTO;
import com.bit.springboard.entity.Board;
import com.bit.springboard.entity.BoardFile;
import com.bit.springboard.entity.CustomUserDetails;
import com.bit.springboard.service.BoardService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

//화면단으로 이동할 때는 ModelAndView객체를 리턴해서 처리
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {
    private final FileUtils fileUtils;
    private final BoardService boardService;

    @GetMapping("/board-list")
    public ResponseEntity<?> getBoardList(@PageableDefault(page = 0, size = 10) Pageable pageable,
                                          @AuthenticationPrincipal CustomUserDetails customUserDetails,
                                          @RequestParam(value = "searchCondition", required = false) String searchCondition,
                                          @RequestParam(value = "searchKeyword", required = false) String searchKeyword) {
        ResponseDTO<BoardDTO> responseDTO = new ResponseDTO<>();

        try {
            searchCondition = searchCondition == null ? "all" : searchCondition;
            searchKeyword = searchKeyword == null ? "" : searchKeyword;

            Page<Board> pageBoard = boardService.getBoardList(pageable, searchCondition, searchKeyword);

            Page<BoardDTO> pageBoardDTO = pageBoard.map(board ->
                    BoardDTO.builder()
                            .boardNo(board.getBoardNo())
                            .boardTitle(board.getBoardTitle())
                            .boardWriter(board.getBoardWriter())
                            .boardContent(board.getBoardContent())
                            .boardRegdate(board.getBoardRegdate().toString())
                            .boardCnt(board.getBoardCnt())
                            .build()
            );

//            List<Board> boardList = boardService.getBoardList();
//
//            List<BoardDTO> boardDTOList = new ArrayList<>();
//
//            for(Board board : boardList) {
//                boardDTOList.add(board.EntityToDTO());
//            }

            responseDTO.setPageItems(pageBoardDTO);
            responseDTO.setStatusCode(HttpStatus.OK.value());

            return ResponseEntity.ok().body(responseDTO);

        } catch(Exception e) {
            responseDTO.setStatusCode(HttpStatus.BAD_REQUEST.value());
            responseDTO.setErrorMessage(e.getMessage());

            return ResponseEntity.badRequest().body(responseDTO);
        }
    }

    //multipart form 데이터 형식을 받기 위해 consumes 속성 지정
    @PostMapping(value = "/board", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> insertBoard(BoardDTO boardDTO,
                                         MultipartHttpServletRequest mphsRequest) {
        ResponseDTO<Map<String, String>> responseDTO =
                new ResponseDTO<Map<String, String>>();
//        String attachPath =
//                request.getSession().getServletContext().getRealPath("/")
//                + "/upload/";


        List<BoardFile> uploadFileList = new ArrayList<BoardFile>();

        try {
            //BoardEntity에 지정한 boardRegdate의 기본값은
            //기본생성자 호출할 때만 기본값으로 지정되는데
            //builder()는 모든 매개변수를 갖는 생성자를 호출하기 때문에
            //boardRegdate의 값이 null값으로 들어간다.
            Board board = Board.builder()
                    .boardTitle(boardDTO.getBoardTitle())
                    .boardContent(boardDTO.getBoardContent())
                    .boardWriter(boardDTO.getBoardWriter())
                    .boardRegdate(LocalDateTime.now())
                    .build();
            System.out.println("========================"+board.getBoardRegdate());

            Iterator<String> iterator = mphsRequest.getFileNames();

            while(iterator.hasNext()) {
                List<MultipartFile> fileList = mphsRequest.getFiles(iterator.next());

                for(MultipartFile multipartFile : fileList) {
                    if(!multipartFile.isEmpty()) {
                        BoardFile boardFile = new BoardFile();

                        boardFile = fileUtils.parseFileInfo(multipartFile, "board/");

                        boardFile.setBoard(board);

                        uploadFileList.add(boardFile);
                    }
                }
            }

            boardService.insertBoard(board, uploadFileList);

            Map<String, String> returnMap =
                    new HashMap<String, String>();

            returnMap.put("msg", "정상적으로 저장되었습니다.");

            responseDTO.setItem(returnMap);

            return ResponseEntity.ok().body(responseDTO);
        } catch(Exception e) {
            responseDTO.setStatusCode(HttpStatus.BAD_REQUEST.value());
            responseDTO.setErrorMessage(e.getMessage());

            return ResponseEntity.badRequest().body(responseDTO);
        }
    }

    @PutMapping(value = "/board")
    public ResponseEntity<?> updateBoard(@RequestPart(value = "boardDTO") BoardDTO boardDTO,
                                         @RequestPart(value = "uploadFiles", required = false) MultipartFile[] uploadFiles,
                                         @RequestPart(value = "changeFileList", required = false) MultipartFile[] changeFileList,
                                         @RequestPart(value = "originFileList", required = false) String originFileList)
            throws Exception {
        System.out.println(boardDTO);
        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();

        List<BoardFileDTO> originFiles = null;

        if(originFileList != null) {
            originFiles = new ObjectMapper().readValue(originFileList,
                    new TypeReference<List<BoardFileDTO>>() {
                    });
        }


        //DB에서 수정, 삭제, 추가 될 파일 정보를 담는 리스트
        List<BoardFile> uFileList = new ArrayList<BoardFile>();

        try {
            Board board = boardDTO.DTOToEntity();
            if(originFiles != null) {
                //파일 처리
                for (int i = 0; i < originFiles.size(); i++) {
                    //수정되는 파일 처리
                    if (originFiles.get(i).getBoardFileStatus().equals("U")) {
                        for (int j = 0; j < changeFileList.length; j++) {
                            if (originFiles.get(i).getNewFileName().equals(
                                    changeFileList[j].getOriginalFilename())) {
                                BoardFile boardFile = new BoardFile();

                                MultipartFile file = changeFileList[j];

                                boardFile = fileUtils.parseFileInfo(file, "board/");

                                boardFile.setBoard(board);
                                boardFile.setBoardFileNo(originFiles.get(i).getBoardFileNo());
                                boardFile.setBoardFileStatus("U");

                                uFileList.add(boardFile);
                            }
                        }
                        //삭제되는 파일 처리
                    } else if (originFiles.get(i).getBoardFileStatus().equals("D")) {
                        BoardFile boardFile = new BoardFile();

                        boardFile.setBoard(board);
                        boardFile.setBoardFileNo(originFiles.get(i).getBoardFileNo());
                        boardFile.setBoardFileStatus("D");

                        uFileList.add(boardFile);
                    }
                }
            }
            //추가된 파일 처리
            if(uploadFiles != null && uploadFiles.length > 0) {
                for(int i = 0; i < uploadFiles.length; i++) {
                    MultipartFile file = uploadFiles[i];

                    if(file.getOriginalFilename() != null &&
                            !file.getOriginalFilename().equals("")) {
                        BoardFile boardFile = new BoardFile();

                        boardFile = fileUtils.parseFileInfo(file, "board/");

                        boardFile.setBoard(board);
                        boardFile.setBoardFileStatus("I");

                        uFileList.add(boardFile);
                    }
                }
            }

            boardService.updateBoard(board, uFileList);

            Map<String, Object> returnMap = new HashMap<>();

            Board updateBoard = boardService.getBoard(board.getBoardNo());
            List<BoardFile> updateBoardFileList =
                    boardService.getBoardFileList(board.getBoardNo());

            BoardDTO returnBoardDTO = updateBoard.EntityToDTO();

            List<BoardFileDTO> boardFileDTOList = new ArrayList<>();

            for(BoardFile boardFile : updateBoardFileList) {
                BoardFileDTO boardFileDTO = boardFile.EntityToDTO();
                boardFileDTOList.add(boardFileDTO);
            }

            returnMap.put("board", returnBoardDTO);
            returnMap.put("boardFileList", boardFileDTOList);

            responseDTO.setItem(returnMap);

            return ResponseEntity.ok().body(responseDTO);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            responseDTO.setStatusCode(HttpStatus.BAD_REQUEST.value());
            responseDTO.setErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(responseDTO);
        }
    }

    @DeleteMapping("/board/{boardNo}")
    public ResponseEntity<?> deleteBoard(@PathVariable int boardNo) {
        ResponseDTO<Map<String, String>> responseDTO =
                new ResponseDTO<Map<String, String>>();

        try {
            boardService.deleteBoard(boardNo);

            Map<String, String> returnMap = new HashMap<String, String>();

            returnMap.put("msg", "정상적으로 삭제되었습니다.");

            responseDTO.setItem(returnMap);

            return ResponseEntity.ok().body(responseDTO);
        } catch (Exception e) {
            responseDTO.setStatusCode(HttpStatus.BAD_REQUEST.value());
            responseDTO.setErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(responseDTO);
        }
    }

    @GetMapping("/board/{boardNo}")
    public ResponseEntity<?> getBoard(@PathVariable int boardNo) {
        ResponseDTO<Map<String, Object>> responseDTO = new ResponseDTO<>();

        try {
            Board board = boardService.getBoard(boardNo);

            BoardDTO returnBoardDTO = board.EntityToDTO();

            List<BoardFile> boardFileList = boardService.getBoardFileList(boardNo);

            List<BoardFileDTO> boardFileDTOList = new ArrayList<>();

            for (BoardFile boardFile : boardFileList) {
                BoardFileDTO boardFileDTO = boardFile.EntityToDTO();
                boardFileDTOList.add(boardFileDTO);
            }

            Map<String, Object> returnMap = new HashMap<>();

            returnMap.put("board", returnBoardDTO);
            returnMap.put("boardFileList", boardFileDTOList);

            responseDTO.setItem(returnMap);
            responseDTO.setStatusCode(HttpStatus.OK.value());

            return ResponseEntity.ok().body(responseDTO);
        } catch (Exception e) {
            responseDTO.setStatusCode(HttpStatus.BAD_REQUEST.value());
            responseDTO.setErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(responseDTO);
        }
    }
}

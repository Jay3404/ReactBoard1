package com.bit.springboard.service.impl;

import com.bit.springboard.entity.Board;
import com.bit.springboard.entity.BoardFile;
import com.bit.springboard.repository.BoardFileRepository;
import com.bit.springboard.repository.BoardRepository;
import com.bit.springboard.service.BoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BoardServiceImpl implements BoardService {

    private BoardRepository boardRepository;

    private BoardFileRepository boardFileRepository;

    //생성자 주입
    @Autowired
    public BoardServiceImpl( BoardRepository boardRepository,
                            BoardFileRepository boardFileRepository) {
        this.boardRepository = boardRepository;
        this.boardFileRepository = boardFileRepository;
    }

    @Override
    public Board getBoard(int boardNo) {
        if(boardRepository.findById(boardNo).isEmpty())
            return null;

        return boardRepository.findById(boardNo).get();
    }

    @Override
    public List<Board> getBoardList() {
        return boardRepository.findAll();
    }

    @Override
    public void insertBoard(Board board, List<BoardFile> uploadFileList) {
        boardRepository.save(board);
        //변경사항 커밋 후 저징
        boardRepository.flush();

        for(BoardFile boardFile : uploadFileList) {
            boardFile.setBoard(board);

            int boardFileNo = boardFileRepository.findMaxFileNo(board.getBoardNo());
            boardFile.setBoardFileNo(boardFileNo);

            boardFileRepository.save(boardFile);
        }
    }

    @Override
    public void updateBoard(Board board, List<BoardFile> uFileList) {
        boardRepository.save(board);

        if(uFileList.size() > 0) {
            for(int i = 0; i < uFileList.size(); i++) {
                if(uFileList.get(i).getBoardFileStatus().equals("U")) {
                    boardFileRepository.save(uFileList.get(i));
                } else if(uFileList.get(i).getBoardFileStatus().equals("D")) {
                    boardFileRepository.delete(uFileList.get(i));
                } else if(uFileList.get(i).getBoardFileStatus().equals("I")) {
                    //추가한 파일들은 boardNo은 가지고 있지만 boardFileNo가 없는 상태라
                    //boardFileNo를 추가
                    int boardFileNo = boardFileRepository.findMaxFileNo(
                            uFileList.get(i).getBoard().getBoardNo());

                    uFileList.get(i).setBoardFileNo(boardFileNo);

                    boardFileRepository.save(uFileList.get(i));
                }
            }
        }
    }

    @Override
    public void deleteBoard(int boardNo) {
        boardRepository.deleteById(boardNo);
    }

    @Override
    public List<BoardFile> getBoardFileList(int boardNo) {

        return boardFileRepository.findByBoardBoardNo(boardNo);
    }

    @Override
    public Page<Board> getBoardList(Pageable pageable, String searchCondition, String searchKeyword) {
        if(searchCondition.equals("all")) {
            if(searchKeyword.equals("")) {
                return boardRepository.findAll(pageable);
            } else {
                return boardRepository.findByBoardTitleContainingOrBoardContentContainingOrBoardWriterContaining(searchKeyword, searchKeyword, searchKeyword, pageable);
            }
        } else {
            if(searchKeyword.equals("")) {
                return boardRepository.findAll(pageable);
            } else {
                if(searchCondition.equals("title")) {
                    return boardRepository.findByBoardTitleContaining(searchKeyword, pageable);
                } else if(searchCondition.equals("content")) {
                    return boardRepository.findByBoardContentContaining(searchKeyword, pageable);
                } else if(searchCondition.equals("writer")) {
                    return boardRepository.findByBoardWriterContaining(searchKeyword, pageable);
                } else {
                    return boardRepository.findAll(pageable);
                }
            }
        }
    }
}
